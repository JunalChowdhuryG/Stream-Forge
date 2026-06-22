package dev.streamforge.core.fsm;

import dev.streamforge.core.fsm.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PipelineFSM - transiciones de estado del pipeline")
class PipelineFSMTest {

    private PipelineFSM fsm;

    @BeforeEach
    void setUp() { fsm = new PipelineFSM("orders-daily-etl"); }

    @Test
    @DisplayName("estado inicial es CREATED")
    void estadoInicial_esCREATED() {
        assertEquals(PipelineState.CREATED, fsm.getCurrentState());
    }

    @Test
    @DisplayName("camino feliz completo: CREATED -> VALIDATED -> RUNNING -> COMPLETED")
    void caminoFeliz_ejecucionExitosa() {
        fsm.validate();
        assertEquals(PipelineState.VALIDATED, fsm.getCurrentState());

        fsm.start();
        assertEquals(PipelineState.RUNNING, fsm.getCurrentState());

        fsm.complete();
        assertEquals(PipelineState.COMPLETED, fsm.getCurrentState());
        assertTrue(fsm.isTerminal());
    }

    @Test
    @DisplayName("camino de fallo y reanudacion: RUNNING -> FAILED -> RESUMING -> RUNNING")
    void caminoReanudacion_fallo() {
        fsm.validate();
        fsm.start();
        fsm.fail();
        assertEquals(PipelineState.FAILED, fsm.getCurrentState());

        fsm.resume();
        assertEquals(PipelineState.RESUMING, fsm.getCurrentState());

        fsm.start();
        assertEquals(PipelineState.RUNNING, fsm.getCurrentState());

        fsm.complete();
        assertTrue(fsm.isTerminal());
    }

    @Test
    @DisplayName("transicion invalida desde CREATED lanza excepcion")
    void transicionInvalida_lanzaExcepcion() {
        assertThrows(InvalidStateTransitionException.class,
            () -> fsm.start()); // CREATED no puede ir a RUNNING directamente
    }

    @Test
    @DisplayName("estado terminal COMPLETED no puede transicionar")
    void estadoTerminal_noTransiciona() {
        fsm.validate(); fsm.start(); fsm.complete();
        assertThrows(InvalidStateTransitionException.class,
            () -> fsm.start());
    }

    @Test
    @DisplayName("listener es notificado en cada transicion")
    void listener_notificadoEnCadaTransicion() {
        AtomicInteger count = new AtomicInteger(0);
        fsm.addListener(e -> count.incrementAndGet());

        fsm.validate(); fsm.start(); fsm.complete();

        assertEquals(3, count.get());
    }

    @Test
    @DisplayName("historial registra todas las transiciones con timestamps")
    void historial_registraTransiciones() {
        fsm.validate(); fsm.start(); fsm.complete();

        var history = fsm.getHistory();
        assertEquals(4, history.size()); // CREATED + 3 transiciones
        assertEquals(PipelineState.CREATED,   history.get(0).to());
        assertEquals(PipelineState.VALIDATED, history.get(1).to());
        assertEquals(PipelineState.RUNNING,   history.get(2).to());
        assertEquals(PipelineState.COMPLETED, history.get(3).to());
    }

    @Test
    @DisplayName("restauracion desde estado persistido funciona correctamente")
    void restauracionDesdeEstadoPersistido() {
        String execId = "exec-abc123";
        PipelineFSM restored = new PipelineFSM(execId, "orders-daily-etl", PipelineState.FAILED);

        assertEquals(PipelineState.FAILED, restored.getCurrentState());
        assertEquals(execId, restored.getExecutionId());

        // Desde FAILED puede reanudar
        restored.resume();
        assertEquals(PipelineState.RESUMING, restored.getCurrentState());
    }
}