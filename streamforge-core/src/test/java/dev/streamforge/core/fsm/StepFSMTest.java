package dev.streamforge.core.fsm;

import dev.streamforge.core.fsm.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StepFSM - transiciones de estado de un paso")
class StepFSMTest {

    @Test
    @DisplayName("camino feliz: PENDING -> RUNNING -> COMPLETED")
    void caminoFeliz_completado() {
        StepFSM fsm = new StepFSM("exec-1", "extract-orders");
        fsm.run();
        fsm.complete();
        assertEquals(StepState.COMPLETED, fsm.getCurrentState());
        assertTrue(fsm.isSuccess());
    }

    @Test
    @DisplayName("camino de skip: PENDING -> SKIPPED (checkpoint existente)")
    void caminoSkip_checkpointExistente() {
        StepFSM fsm = new StepFSM("exec-1", "extract-orders");
        fsm.skip();
        assertEquals(StepState.SKIPPED, fsm.getCurrentState());
        assertTrue(fsm.isSuccess());
        assertTrue(fsm.isTerminal());
    }

    @Test
    @DisplayName("camino de fallo: PENDING -> RUNNING -> FAILED")
    void caminoFallo() {
        StepFSM fsm = new StepFSM("exec-1", "transform-join");
        fsm.run();
        fsm.fail();
        assertEquals(StepState.FAILED, fsm.getCurrentState());
        assertFalse(fsm.isSuccess());
    }

    @Test
    @DisplayName("transicion invalida desde SKIPPED lanza excepcion")
    void transicionDesdeSkipped_invalida() {
        StepFSM fsm = new StepFSM("exec-1", "step-a");
        fsm.skip();
        assertThrows(InvalidStateTransitionException.class, () -> fsm.run());
    }

    @Test
    @DisplayName("todos los estados terminales son identificados correctamente")
    void estadosTerminales() {
        for (StepState state : StepState.values()) {
            boolean expected = state == StepState.COMPLETED
                            || state == StepState.FAILED
                            || state == StepState.SKIPPED;
            assertEquals(expected, state.isTerminal(),
                "Estado " + state + " isTerminal() debe ser " + expected);
        }
    }
}