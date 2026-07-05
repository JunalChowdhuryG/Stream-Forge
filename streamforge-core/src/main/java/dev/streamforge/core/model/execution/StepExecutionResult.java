package dev.streamforge.core.model.execution;

import dev.streamforge.core.fsm.StepState;
import dev.streamforge.core.model.DataBatch;

/**
 * Resultado de la ejecucion de un paso del pipeline.
 * Inmutable - producido por StepExecutor al finalizar cada paso.
 *
 * El reporte de calidad se omite aqui para evitar dependencia circular
 * core -> quality. El StepExecutor lo gestiona directamente.
 */
public record StepExecutionResult(
    String stepId,
    StepState finalState,
    DataBatch outputBatch,
    long rowsRead,
    long rowsWritten,
    long rowsRejected,
    long durationMs,
    String errorMessage,
    boolean wasSkipped
) {
    public boolean isSuccess() {
        return finalState == StepState.COMPLETED || finalState == StepState.SKIPPED;
    }

    public static StepExecutionResult skipped(String stepId) {
        return new StepExecutionResult(
            stepId, StepState.SKIPPED,
            null, 0, 0, 0, 0, null, true
        );
    }

    public static StepExecutionResult failed(String stepId,
                                              String errorMessage,
                                              long durationMs) {
        return new StepExecutionResult(
            stepId, StepState.FAILED,
            null, 0, 0, 0, durationMs, errorMessage, false
        );
    }
}