package dev.streamforge.core.checkpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryCheckpointManager - persistencia y recuperacion de checkpoints")
class InMemoryCheckpointManagerTest {

    private CheckpointManager manager;
    private static final String EXEC_ID = "exec-abc123";

    @BeforeEach
    void setUp() { manager = CheckpointManager.inMemory(); }

    @Test
    @DisplayName("guardar y recuperar checkpoint de un paso")
    void guardarYRecuperar() {
        StepCheckpoint cp = checkpoint(EXEC_ID, "extract-orders", 5000);
        manager.save(cp);

        Optional<StepCheckpoint> found = manager.find(EXEC_ID, "extract-orders");

        assertTrue(found.isPresent());
        assertEquals("extract-orders", found.get().getStepId());
        assertEquals(5000, found.get().getRowsProcessed());
    }

    @Test
    @DisplayName("exists retorna true para paso con checkpoint y false sin el")
    void exists_conYSinCheckpoint() {
        manager.save(checkpoint(EXEC_ID, "step-a", 100));

        assertTrue(manager.exists(EXEC_ID, "step-a"));
        assertFalse(manager.exists(EXEC_ID, "step-b"));
        assertFalse(manager.exists("otra-exec", "step-a"));
    }

    @Test
    @DisplayName("findAll retorna todos los checkpoints de una ejecucion")
    void findAll_todasLasEtapas() {
        manager.save(checkpoint(EXEC_ID, "step-1", 100));
        manager.save(checkpoint(EXEC_ID, "step-2", 200));
        manager.save(checkpoint(EXEC_ID, "step-3", 300));
        manager.save(checkpoint("otra-exec", "step-1", 50));

        var all = manager.findAll(EXEC_ID);

        assertEquals(3, all.size());
        assertTrue(all.stream().allMatch(c -> c.getExecutionId().equals(EXEC_ID)));
    }

    @Test
    @DisplayName("deleteAll elimina solo los checkpoints de la ejecucion indicada")
    void deleteAll_soloLaEjecucionIndicada() {
        manager.save(checkpoint(EXEC_ID, "step-1", 100));
        manager.save(checkpoint(EXEC_ID, "step-2", 200));
        manager.save(checkpoint("otra-exec", "step-1", 50));

        manager.deleteAll(EXEC_ID);

        assertTrue(manager.findAll(EXEC_ID).isEmpty());
        assertFalse(manager.findAll("otra-exec").isEmpty());
    }

    @Test
    @DisplayName("guardar sobre checkpoint existente lo sobreescribe")
    void guardarDosVeces_sobreescribe() {
        manager.save(checkpoint(EXEC_ID, "step-1", 100));
        manager.save(checkpoint(EXEC_ID, "step-1", 999));

        Optional<StepCheckpoint> found = manager.find(EXEC_ID, "step-1");
        assertTrue(found.isPresent());
        assertEquals(999, found.get().getRowsProcessed());
    }

    @Test
    @DisplayName("simulacion de reanudacion: pasos 1-3 tienen checkpoint, paso 4 no")
    void simulacionReanudacion() {
        // Simular que pasos 1-3 completaron antes del fallo
        manager.save(checkpoint(EXEC_ID, "extract-orders",    5000));
        manager.save(checkpoint(EXEC_ID, "extract-customers", 2000));
        manager.save(checkpoint(EXEC_ID, "join",              4800));

        // Verificar que el paso 4 (filter) no tiene checkpoint
        assertTrue(manager.exists(EXEC_ID, "extract-orders"));
        assertTrue(manager.exists(EXEC_ID, "extract-customers"));
        assertTrue(manager.exists(EXEC_ID, "join"));
        assertFalse(manager.exists(EXEC_ID, "filter"),
            "filter no debe tener checkpoint si fallo antes de completar");
    }

    //Helper
    private StepCheckpoint checkpoint(String execId, String stepId, long rows) {
        return new StepCheckpoint(
            execId, stepId, Instant.now(),
            "memory://" + stepId + "/output",
            rows, "sha256-test"
        );
    }
}