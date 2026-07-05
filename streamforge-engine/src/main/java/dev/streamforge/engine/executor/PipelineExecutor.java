package dev.streamforge.engine.executor;

import dev.streamforge.core.checkpoint.CheckpointManager;
import dev.streamforge.core.dag.*;
import dev.streamforge.core.fsm.PipelineFSM;
import dev.streamforge.core.fsm.PipelineState;
import dev.streamforge.core.lineage.LineageGraph;
import dev.streamforge.core.lineage.LineageRepository;
import dev.streamforge.core.metrics.StreamForgeMetrics;
import dev.streamforge.core.model.PipelineDefinition;
import dev.streamforge.core.model.execution.ExecutionContext;
import dev.streamforge.core.model.execution.StepExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.*;
import java.util.concurrent.*;

/**
 * Orquesta la ejecucion completa de un pipeline.
 *
 * Responsabilidades:
 *   1. Construir el DAG y calcular el orden topologico
 *   2. Ejecutar pasos nivel por nivel con paralelismo via Virtual Threads
 *   3. Gestionar el PipelineFSM (CREATED -> RUNNING -> COMPLETED/FAILED)
 *   4. Delegar cada paso al StepExecutor
 *   5. Persistir el grafo de linaje al finalizar
 *   6. Publicar metricas de la ejecucion completa
 */
public class PipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);

    private final DAGBuilder        dagBuilder;
    private final TopologicalSorter sorter;
    private final StepExecutor      stepExecutor;
    private final LineageRepository lineageRepository;
    private final CheckpointManager checkpointManager;
    private final StreamForgeMetrics metrics;
    private final int maxConcurrentSteps;

    public PipelineExecutor(DAGBuilder dagBuilder,
                             TopologicalSorter sorter,
                             StepExecutor stepExecutor,
                             LineageRepository lineageRepository,
                             CheckpointManager checkpointManager,
                             StreamForgeMetrics metrics,
                             int maxConcurrentSteps) {
        this.dagBuilder        = dagBuilder;
        this.sorter            = sorter;
        this.stepExecutor      = stepExecutor;
        this.lineageRepository = lineageRepository;
        this.checkpointManager = checkpointManager;
        this.metrics           = metrics;
        this.maxConcurrentSteps = maxConcurrentSteps;
    }

    /**
     * Resultado de la ejecucion completa del pipeline.
     */
    public record PipelineExecutionResult(
        String executionId,
        String pipelineId,
        PipelineState finalState,
        List<StepExecutionResult> stepResults,
        long totalDurationMs,
        String errorMessage
    ) {
        public boolean isSuccess() { return finalState == PipelineState.COMPLETED; }
        public long totalRowsRead()    {
            return stepResults.stream().mapToLong(StepExecutionResult::rowsRead).sum();
        }
        public long totalRowsWritten() {
            return stepResults.stream().mapToLong(StepExecutionResult::rowsWritten).sum();
        }
    }

    /**
     * Ejecuta el pipeline definido en el contexto dado.
     */
    public PipelineExecutionResult execute(PipelineDefinition pipeline,
                                            ExecutionContext ctx) {
        String executionId = ctx.getExecutionId();
        String pipelineId  = pipeline.getId();

        MDC.put("executionId", executionId);
        MDC.put("pipelineId",  pipelineId);

        log.info("Iniciando ejecucion — pipeline={}, executionId={}",
                pipelineId, executionId);

        PipelineFSM fsm = new PipelineFSM(executionId, pipelineId, PipelineState.CREATED);
        long startMs    = System.currentTimeMillis();

        List<StepExecutionResult> stepResults = new ArrayList<>();
        LineageGraph lineageGraph = new LineageGraph(executionId);

        try {
            // Validar y construir DAG
            fsm.validate();
            PipelineDAG dag      = dagBuilder.build(pipeline);
            TopologicalSorter.SortResult sorted = sorter.sort(dag);

            log.info("DAG construido — {} pasos en {} niveles de paralelismo",
                    sorted.getTotalNodes(), sorted.getLevelCount());

            // Iniciar ejecucion
            fsm.start();

            // Ejecutar nivel por nivel
            for (List<DAGNode> level : sorted.levels()) {
                List<StepExecutionResult> levelResults =
                    executeLevel(level, ctx, lineageGraph);
                stepResults.addAll(levelResults);

                // Si algun paso del nivel fallo, abortar
                boolean levelFailed = levelResults.stream()
                    .anyMatch(r -> !r.isSuccess());

                if (levelFailed) {
                    String failedStep = levelResults.stream()
                        .filter(r -> !r.isSuccess())
                        .map(StepExecutionResult::stepId)
                        .findFirst().orElse("unknown");

                    log.error("Paso fallido — abortando pipeline. stepId={}", failedStep);
                    fsm.fail();

                    long duration = System.currentTimeMillis() - startMs;
                    metrics.recordPipelineExecution(pipelineId, PipelineState.FAILED, duration);

                    return new PipelineExecutionResult(
                        executionId, pipelineId, PipelineState.FAILED,
                        stepResults, duration,
                        "Paso fallido: " + failedStep
                    );
                }
            }

            // Pipeline completado
            fsm.complete();
            long duration = System.currentTimeMillis() - startMs;

            // Persistir linaje
            if (lineageGraph.getNodeCount() > 0) {
                lineageRepository.saveGraph(lineageGraph);
                metrics.recordLineageNodes(pipelineId, lineageGraph.getNodeCount());
            }

            metrics.recordPipelineExecution(pipelineId, PipelineState.COMPLETED, duration);

            log.info("Pipeline completado — executionId={}, duration={}ms, steps={}",
                    executionId, duration, stepResults.size());

            return new PipelineExecutionResult(
                executionId, pipelineId, PipelineState.COMPLETED,
                stepResults, duration, null
            );

        } catch (Exception e) {
            log.error("Error inesperado en pipeline={}: {}", pipelineId, e.getMessage(), e);
            if (!fsm.isTerminal()) fsm.fail();

            long duration = System.currentTimeMillis() - startMs;
            metrics.recordPipelineExecution(pipelineId, PipelineState.FAILED, duration);

            return new PipelineExecutionResult(
                executionId, pipelineId, PipelineState.FAILED,
                stepResults, duration, e.getMessage()
            );
        } finally {
            MDC.clear();
        }
    }

    //Privados
    private List<StepExecutionResult> executeLevel(List<DAGNode> level,
                                                    ExecutionContext ctx,
                                                    LineageGraph lineageGraph) {
        if (level.size() == 1) {
            // Un solo paso: ejecutar directamente sin overhead de threads
            return List.of(stepExecutor.execute(level.get(0).getStep(), ctx, lineageGraph));
        }

        // Multiples pasos independientes: ejecutar en paralelo con Virtual Threads
        log.info("Ejecutando {} pasos en paralelo", level.size());

        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(level.size(), maxConcurrentSteps),
            r -> Thread.ofVirtual().name("sf-step-executor").unstarted(r)
        );

        List<Future<StepExecutionResult>> futures = new ArrayList<>();
        for (DAGNode node : level) {
            futures.add(executor.submit(() ->
                stepExecutor.execute(node.getStep(), ctx, lineageGraph)
            ));
        }

        executor.shutdown();
        List<StepExecutionResult> results = new ArrayList<>();

        for (Future<StepExecutionResult> future : futures) {
            try {
                results.add(future.get(300, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                results.add(StepExecutionResult.failed(
                    "unknown", "Timeout en ejecucion paralela", 300_000));
            } catch (Exception e) {
                results.add(StepExecutionResult.failed(
                    "unknown", e.getMessage(), 0));
            }
        }

        return results;
    }
}