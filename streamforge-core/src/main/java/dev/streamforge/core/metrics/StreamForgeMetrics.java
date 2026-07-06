package dev.streamforge.core.metrics;

import dev.streamforge.core.fsm.PipelineState;
import dev.streamforge.core.fsm.StepState;

/**
 * Interfaz de publicacion de metricas del motor de pipelines.
 *
 * El core depende de esta abstraccion - la implementacion concreta
 * (Prometheus via Micrometer) vive en streamforge-engine.
 * Implementacion NOOP disponible para tests sin infraestructura.
 */
public interface StreamForgeMetrics {

    void recordPipelineExecution(String pipelineId, PipelineState finalState,
                                  long durationMs);

    void recordStepExecution(String pipelineId, String stepId,
                              StepState finalState, long durationMs);

    void recordRowsProcessed(String pipelineId, String stepId,
                              long rowsRead, long rowsWritten, long rowsRejected);

    void recordQualityFailureRate(String pipelineId, String stepId,
                                   String fieldName, double failureRate);

    void recordCheckpoint(String pipelineId, boolean isResumption);

    void recordLineageNodes(String pipelineId, int nodeCount);

    void recordConnectorError(String connectorType, String operation);

    void recordSchemaDrift(String pipelineId, String datasetName);

    /**
     * Implementacion nula para tests y modo standalone.
     */
    StreamForgeMetrics NOOP = new StreamForgeMetrics() {
        public void recordPipelineExecution(String p, PipelineState s, long d) {}
        public void recordStepExecution(String p, String s, StepState st, long d) {}
        public void recordRowsProcessed(String p, String s, long r, long w, long rj) {}
        public void recordQualityFailureRate(String p, String s, String f, double r) {}
        public void recordCheckpoint(String p, boolean r) {}
        public void recordLineageNodes(String p, int n) {}
        public void recordConnectorError(String c, String o) {}
        public void recordSchemaDrift(String p, String d) {}
    };
}