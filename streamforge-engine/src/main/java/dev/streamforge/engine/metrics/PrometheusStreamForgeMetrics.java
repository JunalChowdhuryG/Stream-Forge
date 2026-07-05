package dev.streamforge.engine.metrics;

import dev.streamforge.core.fsm.PipelineState;
import dev.streamforge.core.fsm.StepState;
import dev.streamforge.core.metrics.StreamForgeMetrics;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementacion de StreamForgeMetrics usando Micrometer + Prometheus.
 * Publica las 11 metricas definidas en la arquitectura con prefijo streamforge_.
 */
@Component
public class PrometheusStreamForgeMetrics implements StreamForgeMetrics {

    private final MeterRegistry registry;

    // Gauges con estado mutable
    private final ConcurrentHashMap<String, AtomicLong> lineageNodeCounts
            = new ConcurrentHashMap<>();

    public PrometheusStreamForgeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordPipelineExecution(String pipelineId,
                                         PipelineState finalState,
                                         long durationMs) {
        Counter.builder("streamforge.pipeline.executions.total")
            .description("Ejecuciones de pipeline por estado final")
            .tag("pipeline_id", pipelineId)
            .tag("state",       finalState.name())
            .register(registry)
            .increment();

        DistributionSummary.builder("streamforge.pipeline.duration.ms")
            .description("Duracion de ejecuciones de pipeline en ms")
            .tag("pipeline_id", pipelineId)
            .serviceLevelObjectives(1000, 5000, 30000, 60000, 300000)
            .register(registry)
            .record(durationMs);
    }

    @Override
    public void recordStepExecution(String pipelineId, String stepId,
                                     StepState finalState, long durationMs) {
        Counter.builder("streamforge.step.executions.total")
            .description("Ejecuciones de paso por estado final")
            .tag("pipeline_id", pipelineId)
            .tag("step_id",     stepId)
            .tag("state",       finalState.name())
            .register(registry)
            .increment();

        DistributionSummary.builder("streamforge.step.duration.ms")
            .description("Duracion de ejecuciones de paso en ms")
            .tag("pipeline_id", pipelineId)
            .tag("step_id",     stepId)
            .serviceLevelObjectives(100, 500, 1000, 5000, 30000)
            .register(registry)
            .record(durationMs);
    }

    @Override
    public void recordRowsProcessed(String pipelineId, String stepId,
                                     long rowsRead, long rowsWritten,
                                     long rowsRejected) {
        Counter.builder("streamforge.rows.processed.total")
            .description("Filas procesadas por pipeline y paso")
            .tag("pipeline_id", pipelineId)
            .tag("step_id",     stepId)
            .tag("operation",   "read")
            .register(registry)
            .increment(rowsRead);

        Counter.builder("streamforge.rows.processed.total")
            .tag("pipeline_id", pipelineId)
            .tag("step_id",     stepId)
            .tag("operation",   "write")
            .register(registry)
            .increment(rowsWritten);

        if (rowsRejected > 0) {
            Counter.builder("streamforge.rows.rejected.total")
                .description("Filas rechazadas por reglas de calidad")
                .tag("pipeline_id", pipelineId)
                .tag("step_id",     stepId)
                .register(registry)
                .increment(rowsRejected);
        }
    }

    @Override
    public void recordQualityFailureRate(String pipelineId, String stepId,
                                          String fieldName, double failureRate) {
        Gauge.builder("streamforge.quality.failure.rate",
                () -> failureRate)
            .description("Tasa de fallos de calidad por campo")
            .tag("pipeline_id", pipelineId)
            .tag("step_id",     stepId)
            .tag("field",       fieldName)
            .register(registry);
    }

    @Override
    public void recordCheckpoint(String pipelineId, boolean isResumption) {
        Counter.builder("streamforge.checkpoints.total")
            .description("Checkpoints guardados y reanudaciones")
            .tag("pipeline_id", pipelineId)
            .tag("type", isResumption ? "resumption" : "checkpoint")
            .register(registry)
            .increment();
    }

    @Override
    public void recordLineageNodes(String pipelineId, int nodeCount) {
        String key = pipelineId;
        lineageNodeCounts.computeIfAbsent(key, k -> {
            AtomicLong gauge = new AtomicLong(nodeCount);
            Gauge.builder("streamforge.lineage.nodes.total", gauge, AtomicLong::get)
                .description("Nodos en el grafo de linaje por pipeline")
                .tag("pipeline_id", pipelineId)
                .register(registry);
            return gauge;
        }).set(nodeCount);
    }

    @Override
    public void recordConnectorError(String connectorType, String operation) {
        Counter.builder("streamforge.connector.errors.total")
            .description("Errores de conector por tipo y operacion")
            .tag("connector", connectorType)
            .tag("operation", operation)
            .register(registry)
            .increment();
    }

    @Override
    public void recordSchemaDrift(String pipelineId, String datasetName) {
        Counter.builder("streamforge.schema.drifts.total")
            .description("Eventos de schema drift detectados")
            .tag("pipeline_id",   pipelineId)
            .tag("dataset_name",  datasetName)
            .register(registry)
            .increment();
    }
}