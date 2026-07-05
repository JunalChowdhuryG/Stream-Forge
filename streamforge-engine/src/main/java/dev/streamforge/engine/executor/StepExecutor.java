package dev.streamforge.engine.executor;

import dev.streamforge.connectors.api.Connector;
import dev.streamforge.connectors.api.ConnectorConfig;
import dev.streamforge.connectors.api.WriteResult;
import dev.streamforge.connectors.registry.ConnectorRegistry;
import dev.streamforge.core.checkpoint.CheckpointManager;
import dev.streamforge.core.checkpoint.StepCheckpoint;
import dev.streamforge.core.fsm.StepFSM;
import dev.streamforge.core.fsm.StepState;
import dev.streamforge.core.lineage.LineageGraph;
import dev.streamforge.core.lineage.LineageRecorder;
import dev.streamforge.core.metrics.StreamForgeMetrics;
import dev.streamforge.core.model.DataBatch;
import dev.streamforge.core.model.StepDefinition;
import dev.streamforge.core.model.StepType;
import dev.streamforge.core.model.execution.ExecutionContext;
import dev.streamforge.core.model.execution.StepExecutionResult;
import dev.streamforge.quality.engine.QualityRuleEngine;
import dev.streamforge.quality.model.QualityReport;
import dev.streamforge.quality.model.QualityRule;
import dev.streamforge.quality.model.RuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.*;

/**
 * Ejecuta un paso individual de un pipeline.
 *
 * Responsabilidades:
 *   1. Verificar si el paso tiene checkpoint (SKIP si existe)
 *   2. Ejecutar el conector SOURCE o SINK
 *   3. Aplicar reglas de calidad si estan configuradas
 *   4. Registrar linaje via LineageRecorder
 *   5. Guardar checkpoint al completar
 *   6. Publicar metricas
 *   7. Reintentar con backoff exponencial ante fallos transitorios
 */
public class StepExecutor {

    private static final Logger log = LoggerFactory.getLogger(StepExecutor.class);

    private final ConnectorRegistry connectorRegistry;
    private final CheckpointManager checkpointManager;
    private final QualityRuleEngine qualityEngine;
    private final StreamForgeMetrics metrics;
    private final int retryMaxAttempts;
    private final long retryInitialDelayMs;
    private final long retryMaxDelayMs;

    public StepExecutor(ConnectorRegistry connectorRegistry,
                         CheckpointManager checkpointManager,
                         QualityRuleEngine qualityEngine,
                         StreamForgeMetrics metrics,
                         int retryMaxAttempts,
                         long retryInitialDelayMs,
                         long retryMaxDelayMs) {
        this.connectorRegistry  = connectorRegistry;
        this.checkpointManager  = checkpointManager;
        this.qualityEngine      = qualityEngine;
        this.metrics            = metrics;
        this.retryMaxAttempts   = retryMaxAttempts;
        this.retryInitialDelayMs = retryInitialDelayMs;
        this.retryMaxDelayMs    = retryMaxDelayMs;
    }

    /**
     * Ejecuta el paso dado en el contexto de ejecucion.
     */
    public StepExecutionResult execute(StepDefinition step,
                                        ExecutionContext ctx,
                                        LineageGraph lineageGraph) {
        String executionId = ctx.getExecutionId();
        String stepId      = step.getId();
        String pipelineId  = ctx.getPipeline().getId();

        // MDC para logging estructurado
        MDC.put("executionId", executionId);
        MDC.put("pipelineId",  pipelineId);
        MDC.put("stepId",      stepId);

        try {
            // Paso 1: verificar checkpoint
            if (checkpointManager.exists(executionId, stepId)) {
                log.info("Paso con checkpoint — saltando: stepId={}", stepId);
                metrics.recordCheckpoint(pipelineId, true);
                return StepExecutionResult.skipped(stepId);
            }

            StepFSM fsm = new StepFSM(executionId, stepId);
            fsm.run();

            return executeWithRetry(step, ctx, lineageGraph, fsm, pipelineId);

        } finally {
            MDC.clear();
        }
    }

    //Privados
    private StepExecutionResult executeWithRetry(StepDefinition step,
                                                   ExecutionContext ctx,
                                                   LineageGraph lineageGraph,
                                                   StepFSM fsm,
                                                   String pipelineId) {
        long startMs = System.currentTimeMillis();
        int attempt  = 0;
        long delay   = retryInitialDelayMs;

        while (attempt <= retryMaxAttempts) {
            try {
                StepExecutionResult result =
                    executeStep(step, ctx, lineageGraph, fsm, pipelineId, startMs);

                metrics.recordStepExecution(pipelineId, step.getId(),
                        StepState.COMPLETED,
                        System.currentTimeMillis() - startMs);
                metrics.recordRowsProcessed(pipelineId, step.getId(),
                        result.rowsRead(), result.rowsWritten(), result.rowsRejected());

                return result;

            } catch (Exception e) {
                attempt++;
                long duration = System.currentTimeMillis() - startMs;

                if (attempt > retryMaxAttempts) {
                    log.error("Paso fallido tras {} intentos — stepId={}, error={}",
                            attempt, step.getId(), e.getMessage());
                    fsm.fail();
                    metrics.recordStepExecution(pipelineId, step.getId(),
                            StepState.FAILED, duration);
                    metrics.recordConnectorError(
                        step.getConnector() != null ? step.getConnector() : "transform",
                        step.getType().name().toLowerCase()
                    );
                    return StepExecutionResult.failed(step.getId(), e.getMessage(), duration);
                }

                log.warn("Intento {} fallido para stepId={}, reintentando en {}ms — {}",
                        attempt, step.getId(), delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                delay = Math.min(delay * 2, retryMaxDelayMs);
            }
        }

        fsm.fail();
        return StepExecutionResult.failed(step.getId(), "Interrumpido", 0);
    }

    private StepExecutionResult executeStep(StepDefinition step,
                                             ExecutionContext ctx,
                                             LineageGraph lineageGraph,
                                             StepFSM fsm,
                                             String pipelineId,
                                             long startMs) {
        LineageRecorder recorder = new LineageRecorder(lineageGraph);
        DataBatch outputBatch    = null;
        long rowsRead = 0, rowsWritten = 0, rowsRejected = 0;
        QualityReport qualityReport = null;

        if (step.getType() == StepType.SOURCE) {
            Connector connector = connectorRegistry.resolveOrThrow(step.getConnector());
            ConnectorConfig config = new ConnectorConfig(step.getConnector(), step.getConfig());

            outputBatch = connector.read(config, ctx);
            rowsRead    = outputBatch.getRowCount();

            recorder.recordSource(outputBatch, step.getId());
            ctx.setStepOutput(step.getOutput(), outputBatch);

            log.info("SOURCE completado — stepId={}, rows={}", step.getId(), rowsRead);

        } else if (step.getType() == StepType.TRANSFORM) {
            outputBatch = applyTransform(step, ctx, recorder);
            rowsRead    = outputBatch.getRowCount();
            ctx.setStepOutput(step.getOutput(), outputBatch);

            log.info("TRANSFORM completado — stepId={}, rows={}", step.getId(), rowsRead);

        } else if (step.getType() == StepType.SINK) {
            DataBatch inputBatch = ctx.getStepOutput(step.getInput())
                .orElseThrow(() -> new IllegalStateException(
                    "No hay output disponible para el input '" + step.getInput()
                    + "' requerido por paso '" + step.getId() + "'"));

            rowsRead = inputBatch.getRowCount();

            // Evaluar calidad antes de escribir
            if (step.getQuality() != null && !step.getQuality().getRules().isEmpty()) {
                qualityReport = evaluateQuality(step, inputBatch, ctx, pipelineId);
                rowsRejected  = qualityReport.getFailingFields().stream()
                        .mapToLong(f -> f.getFailed())
                        .sum();

                publishQualityMetrics(pipelineId, step.getId(), qualityReport);
            }

            Connector connector = connectorRegistry.resolveOrThrow(step.getConnector());
            ConnectorConfig config = new ConnectorConfig(step.getConnector(), step.getConfig());
            WriteResult writeResult = connector.write(inputBatch, config, ctx);

            rowsWritten  = writeResult.rowsWritten();
            rowsRejected = writeResult.rowsRejected();

            String destDataset = step.getConfig().getOrDefault("table",
                step.getConfig().getOrDefault("topic",
                step.getConfig().getOrDefault("path", "unknown"))).toString();
            recorder.recordSink(inputBatch, destDataset, step.getId());

            log.info("SINK completado — stepId={}, written={}, rejected={}",
                    step.getId(), rowsWritten, rowsRejected);
        }

        // Guardar checkpoint
        long duration = System.currentTimeMillis() - startMs;
        StepCheckpoint checkpoint = new StepCheckpoint(
            ctx.getExecutionId(), step.getId(), Instant.now(),
            step.getOutput(), rowsRead, null
        );
        checkpointManager.save(checkpoint);
        metrics.recordCheckpoint(pipelineId, false);
        fsm.complete();

        return new StepExecutionResult(
        step.getId(), StepState.COMPLETED,
        outputBatch, rowsRead, rowsWritten, rowsRejected,
        duration, null, false
    );
    }

    private DataBatch applyTransform(StepDefinition step, ExecutionContext ctx,
                                      LineageRecorder recorder) {
        String transformType = step.getTransform();

        if ("filter".equalsIgnoreCase(transformType)) {
            DataBatch input = ctx.getStepOutput(
                step.getDependsOn().get(0).replace("-", "_"))
                .or(() -> findInputBatch(step, ctx))
                .orElseThrow(() -> new IllegalStateException(
                    "No hay input para el transform filter en paso: " + step.getId()));

            // Filter: todas las filas pasan (la condicion se aplica en el conector)
            // Por ahora retornamos el batch completo — la logica de filtro
            // se implementa en H8 con el ExpressionEvaluator
            recorder.recordFilter(input,
                buildOutputBatch(input, step.getOutput()), step.getId());
            return buildOutputBatch(input, step.getOutput());

        } else if ("join".equalsIgnoreCase(transformType)) {
            Map<String, Object> config = step.getConfig();
            String leftDataset  = config.get("left").toString();
            String rightDataset = config.get("right").toString();

            DataBatch left  = ctx.getStepOutput(leftDataset)
                .orElseThrow(() -> new IllegalStateException(
                    "Dataset '" + leftDataset + "' no encontrado para JOIN"));
            DataBatch right = ctx.getStepOutput(rightDataset)
                .orElseThrow(() -> new IllegalStateException(
                    "Dataset '" + rightDataset + "' no encontrado para JOIN"));

            DataBatch output = performJoin(left, right, config, step.getOutput());
            recorder.recordJoin(left, right, output, step.getId());
            return output;
        }

        // Transform desconocido: pasar los datos tal como estan
        log.warn("Transform desconocido '{}' — pasando datos sin modificar", transformType);
        DataBatch input = findInputBatch(step, ctx)
            .orElseThrow(() -> new IllegalStateException(
                "No hay input para paso: " + step.getId()));
        return buildOutputBatch(input, step.getOutput());
    }

    private DataBatch performJoin(DataBatch left, DataBatch right,
                                   Map<String, Object> config, String outputDataset) {
        String joinKey = config.get("on").toString();
        String joinType = config.getOrDefault("type", "INNER").toString();

        // Index del right por join key
        Map<Object, Map<String, Object>> rightIndex = new HashMap<>();
        for (Map<String, Object> row : right.getRows()) {
            Object keyVal = row.get(joinKey);
            if (keyVal != null) rightIndex.put(keyVal, row);
        }

        // Merge de schemas
        List<dev.streamforge.core.model.FieldDefinition> mergedFields =
            new ArrayList<>(left.getSchema().getFields());
        for (dev.streamforge.core.model.FieldDefinition f : right.getSchema().getFields()) {
            if (!left.getSchema().hasField(f.getName())) {
                mergedFields.add(f);
            }
        }
        dev.streamforge.core.model.DataSchema mergedSchema =
            new dev.streamforge.core.model.DataSchema(outputDataset, mergedFields);

        DataBatch.Builder builder = DataBatch.builder()
                .datasetName(outputDataset)
                .schema(mergedSchema);

        for (Map<String, Object> leftRow : left.getRows()) {
            Object keyVal   = leftRow.get(joinKey);
            Map<String, Object> rightRow = rightIndex.get(keyVal);

            if (rightRow != null) {
                Map<String, Object> merged = new LinkedHashMap<>(leftRow);
                rightRow.forEach(merged::putIfAbsent);
                builder.addRow(merged);
            } else if ("LEFT".equalsIgnoreCase(joinType)) {
                builder.addRow(new LinkedHashMap<>(leftRow));
            }
        }

        return builder.build();
    }

    private Optional<DataBatch> findInputBatch(StepDefinition step,
                                                ExecutionContext ctx) {
        if (step.getInput() != null) {
            return ctx.getStepOutput(step.getInput());
        }
        if (!step.getDependsOn().isEmpty()) {
            for (String dep : step.getDependsOn()) {
                Optional<DataBatch> batch = ctx.getStepOutput(dep);
                if (batch.isPresent()) return batch;
                // Intentar con el nombre del output del paso anterior
                Optional<DataBatch> byOutput = ctx.getAllOutputs().values().stream()
                    .filter(b -> b.getDatasetName().startsWith(dep.replace("-", "_")))
                    .findFirst();
                if (byOutput.isPresent()) return byOutput;
            }
        }
        return Optional.empty();
    }

    private DataBatch buildOutputBatch(DataBatch input, String outputDatasetName) {
        String dataset = outputDatasetName != null ? outputDatasetName : input.getDatasetName();
        return DataBatch.builder()
                .datasetName(dataset)
                .schema(new dev.streamforge.core.model.DataSchema(
                    dataset, input.getSchema().getFields()))
                .rows(input.getRows())
                .totalRowsFromSource(input.getTotalRowsFromSource())
                .build();
    }

    @SuppressWarnings("unchecked")
    private QualityReport evaluateQuality(StepDefinition step, DataBatch batch,
                                           ExecutionContext ctx, String pipelineId) {
        List<QualityRule> rules = new ArrayList<>();
        double threshold = 0.95;

        if (step.getQuality() != null) {
            threshold = step.getQuality().getThreshold();
            for (Map<String, Object> ruleMap : step.getQuality().getRules()) {
                String fieldName = (String) ruleMap.get("field");
                List<Map<String, Object>> checks =
                    (List<Map<String, Object>>) ruleMap.get("checks");
                if (checks != null) {
                    for (Map<String, Object> check : checks) {
                        String typeStr = (String) check.get("type");
                        Map<String, Object> params = new HashMap<>(check);
                        params.remove("type");
                        rules.add(new QualityRule(fieldName,
                            RuleType.valueOf(typeStr), params));
                    }
                }
            }
        }

        return qualityEngine.evaluate(batch, rules, threshold,
                ctx.getExecutionId(), step.getId());
    }

    private void publishQualityMetrics(String pipelineId, String stepId,
                                        QualityReport report) {
        for (var result : report.getFieldResults()) {
            if (result.getFailureRate() > 0) {
                metrics.recordQualityFailureRate(
                    pipelineId, stepId,
                    result.getFieldName(),
                    result.getFailureRate()
                );
            }
        }
    }
}