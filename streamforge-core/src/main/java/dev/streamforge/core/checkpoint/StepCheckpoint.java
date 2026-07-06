package dev.streamforge.core.checkpoint;

import java.time.Instant;

/**
 * Registro de que un paso completo exitosamente en una ejecucion
 *
 * El checkpoint funciona como contrato: si existe un checkpoint valido
 * para un paso, su output es confiable y no necesita re-ejecutarse
 *
 * Se persiste en la tabla step_checkpoints de PostgreSQL
 */
public class StepCheckpoint {

    private final String executionId;
    private final String stepId;
    private final Instant completedAt;
    private final String outputLocation;
    private final long rowsProcessed;
    private final String checksum;

    public StepCheckpoint(String executionId,
                          String stepId,
                          Instant completedAt,
                          String outputLocation,
                          long rowsProcessed,
                          String checksum) {
        this.executionId     = executionId;
        this.stepId          = stepId;
        this.completedAt     = completedAt;
        this.outputLocation  = outputLocation;
        this.rowsProcessed   = rowsProcessed;
        this.checksum        = checksum;
    }

    public String getExecutionId()    { return executionId;    }
    public String getStepId()         { return stepId;         }
    public Instant getCompletedAt()   { return completedAt;    }
    public String getOutputLocation() { return outputLocation; }
    public long getRowsProcessed()    { return rowsProcessed;  }
    public String getChecksum()       { return checksum;       }

    @Override
    public String toString() {
        return "StepCheckpoint{executionId='" + executionId
                + "', stepId='" + stepId
                + "', rows=" + rowsProcessed + "}";
    }
}