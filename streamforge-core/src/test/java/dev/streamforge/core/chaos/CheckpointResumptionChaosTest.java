package dev.streamforge.core.chaos;

import dev.streamforge.core.checkpoint.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P2 - Chaos: reanudacion correcta desde checkpoint")
class CheckpointResumptionChaosTest {

    @Test
    @DisplayName("pipeline de 10 pasos con fallo en paso 7 reanuda desde paso 7")
    void reanudacion_desdeElPasoFallido() {
        CheckpointManager manager  = CheckpointManager.inMemory();
        String executionId         = "exec-resumption-test";
        int totalSteps             = 10;
        int failedAtStep           = 7;

        // Simular que pasos 1-6 completaron antes del fallo
        for (int i = 1; i < failedAtStep; i++) {
            manager.save(new StepCheckpoint(
                executionId, "step-" + i,
                Instant.now(), "memory://step-" + i,
                1000L * i, null
            ));
        }

        // Verificar que los pasos 1-6 tienen checkpoint
        for (int i = 1; i < failedAtStep; i++) {
            assertTrue(manager.exists(executionId, "step-" + i),
                "Paso step-" + i + " debe tener checkpoint");
        }

        // Verificar que los pasos 7-10 NO tienen checkpoint
        for (int i = failedAtStep; i <= totalSteps; i++) {
            assertFalse(manager.exists(executionId, "step-" + i),
                "Paso step-" + i + " NO debe tener checkpoint");
        }

        // Contar cuantos pasos se saltarian en la reanudacion
        List<StepCheckpoint> completed = manager.findAll(executionId);
        assertEquals(failedAtStep - 1, completed.size(),
            "Deben existir " + (failedAtStep - 1) + " checkpoints para saltarlos");

        // El primer paso sin checkpoint es donde reanuda
        int firstToExecute = failedAtStep;
        assertFalse(manager.exists(executionId, "step-" + firstToExecute),
            "La reanudacion debe comenzar en step-" + firstToExecute);
    }

    @Test
    @DisplayName("reanudacion con todos los checkpoints completa sin re-ejecutar nada")
    void reanudacion_todosCheckpoints_completaSinEjecutar() {
        CheckpointManager manager = CheckpointManager.inMemory();
        String executionId        = "exec-all-checkpointed";
        int totalSteps            = 5;

        for (int i = 1; i <= totalSteps; i++) {
            manager.save(new StepCheckpoint(
                executionId, "step-" + i,
                Instant.now(), "memory://output-" + i, 100L, null
            ));
        }

        // Todos los pasos deben tener checkpoint
        for (int i = 1; i <= totalSteps; i++) {
            assertTrue(manager.exists(executionId, "step-" + i));
        }

        assertEquals(totalSteps, manager.findAll(executionId).size());
    }
}