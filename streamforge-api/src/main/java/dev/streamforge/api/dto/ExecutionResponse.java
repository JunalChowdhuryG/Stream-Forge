package dev.streamforge.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta de la API para una ejecucion de pipeline.
 */
public record ExecutionResponse(
    String executionId,
    String pipelineId,
    String state,
    long totalRowsRead,
    long totalRowsWritten,
    long durationMs,
    List<StepResponse> steps,
    String errorMessage,
    Instant startedAt
) {
    public record StepResponse(
        String stepId,
        String state,
        long rowsRead,
        long rowsWritten,
        long rowsRejected,
        long durationMs,
        boolean wasSkipped
    ) {}
}