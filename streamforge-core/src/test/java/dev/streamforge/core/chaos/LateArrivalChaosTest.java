package dev.streamforge.core.chaos;

import dev.streamforge.core.checkpoint.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P6 - Chaos: datos tardios y ventanas de retencion de checkpoints")
class LateArrivalChaosTest {

    @Test
    @DisplayName("checkpoint antiguo sigue siendo valido para reanudacion")
    void checkpointAntiguo_validoParaReanudacion() {
        CheckpointManager manager = CheckpointManager.inMemory();
        String executionId        = "exec-late-test";

        // Guardar checkpoint con timestamp de hace 2 horas
        Instant oldTimestamp = Instant.now().minus(2, ChronoUnit.HOURS);
        manager.save(new StepCheckpoint(
            executionId, "extract-step",
            oldTimestamp, "memory://output", 5000L, null
        ));

        // El checkpoint debe seguir siendo accesible para reanudacion
        assertTrue(manager.exists(executionId, "extract-step"),
            "Checkpoint antiguo debe seguir siendo valido para reanudacion");

        var checkpoint = manager.find(executionId, "extract-step");
        assertTrue(checkpoint.isPresent());
        assertEquals(5000L, checkpoint.get().getRowsProcessed());
        assertEquals(oldTimestamp, checkpoint.get().getCompletedAt());
    }

    @Test
    @DisplayName("multiples ejecuciones del mismo pipeline tienen checkpoints independientes")
    void multiplesEjecuciones_checkpointsIndependientes() {
        CheckpointManager manager = CheckpointManager.inMemory();

        String exec1 = "exec-run-1";
        String exec2 = "exec-run-2";

        manager.save(new StepCheckpoint(
            exec1, "step-a", Instant.now(), null, 1000L, null));
        manager.save(new StepCheckpoint(
            exec1, "step-b", Instant.now(), null, 2000L, null));
        manager.save(new StepCheckpoint(
            exec2, "step-a", Instant.now(), null, 3000L, null));

        // exec1 tiene 2 checkpoints, exec2 tiene 1
        List<StepCheckpoint> exec1Checkpoints = manager.findAll(exec1);
        List<StepCheckpoint> exec2Checkpoints = manager.findAll(exec2);

        assertEquals(2, exec1Checkpoints.size());
        assertEquals(1, exec2Checkpoints.size());

        // Los checkpoints de exec1 no interfieren con exec2
        assertFalse(manager.exists(exec2, "step-b"),
            "step-b de exec1 no debe aparecer en exec2");

        // Los valores son independientes
        assertEquals(3000L, exec2Checkpoints.get(0).getRowsProcessed());
    }

    @Test
    @DisplayName("deleteAll limpia solo la ejecucion indicada sin afectar otras")
    void deleteAll_soloLaEjecucionIndicada() {
        CheckpointManager manager = CheckpointManager.inMemory();

        manager.save(new StepCheckpoint("exec-a", "step-1",
            Instant.now(), null, 100L, null));
        manager.save(new StepCheckpoint("exec-a", "step-2",
            Instant.now(), null, 200L, null));
        manager.save(new StepCheckpoint("exec-b", "step-1",
            Instant.now(), null, 300L, null));

        manager.deleteAll("exec-a");

        assertTrue(manager.findAll("exec-a").isEmpty(),
            "exec-a debe quedar sin checkpoints");
        assertFalse(manager.findAll("exec-b").isEmpty(),
            "exec-b no debe verse afectado");
        assertEquals(300L, manager.findAll("exec-b").get(0).getRowsProcessed());
    }
}