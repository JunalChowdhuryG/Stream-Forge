package dev.streamforge.core.fsm;

import dev.streamforge.core.fsm.exception.InvalidStateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Maquina de estados para el ciclo de vida de una ejecucion de pipeline
 * Garantiza que solo ocurran transiciones validas segun la matriz
 * definida en PipelineState. Registra historial con timestamps
 * Thread-safe: transiciones sincronizadas
 */
public class PipelineFSM {

    private static final Logger log = LoggerFactory.getLogger(PipelineFSM.class);

    private final String executionId;
    private final String pipelineId;
    private volatile PipelineState currentState;
    private final List<StateTransitionEvent<PipelineState>> history;
    private final List<Consumer<StateTransitionEvent<PipelineState>>> listeners;

    public PipelineFSM(String pipelineId) {
        this.executionId  = UUID.randomUUID().toString();
        this.pipelineId   = pipelineId;
        this.currentState = PipelineState.CREATED;
        this.history      = new ArrayList<>();
        this.listeners    = new ArrayList<>();

        history.add(new StateTransitionEvent<>(executionId, null, PipelineState.CREATED, Instant.now()));
        log.debug("PipelineFSM creado - executionId={}, pipeline={}", executionId, pipelineId);
    }

    /**
     * Reanuda un pipeline desde un estado persistido (crash recovery)
     */
    public PipelineFSM(String executionId, String pipelineId, PipelineState restoredState) {
        this.executionId  = executionId;
        this.pipelineId   = pipelineId;
        this.currentState = restoredState;
        this.history      = new ArrayList<>();
        this.listeners    = new ArrayList<>();

        history.add(new StateTransitionEvent<>(executionId, null, restoredState, Instant.now()));
        log.info("PipelineFSM restaurado - executionId={}, state={}", executionId, restoredState);
    }

    public synchronized void transition(PipelineState target) {
        if (!currentState.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                "PipelineFSM", currentState, target, currentState.validTransitions()
            );
        }

        PipelineState previous = currentState;
        currentState = target;

        StateTransitionEvent<PipelineState> event =
            new StateTransitionEvent<>(executionId, previous, target, Instant.now());
        history.add(event);
        listeners.forEach(l -> l.accept(event));

        log.info("Pipeline FSM - executionId={}, {} -> {}", executionId, previous, target);
    }

    public synchronized void addListener(Consumer<StateTransitionEvent<PipelineState>> l) {
        listeners.add(l);
    }

    // Metodos de conveniencia
    public void validate()  { transition(PipelineState.VALIDATED); }
    public void start()     { transition(PipelineState.RUNNING);   }
    public void complete()  { transition(PipelineState.COMPLETED); }
    public void fail()      { transition(PipelineState.FAILED);    }
    public void resume()    { transition(PipelineState.RESUMING);  }

    public PipelineState getCurrentState()   { return currentState;  }
    public String getExecutionId()           { return executionId;   }
    public String getPipelineId()            { return pipelineId;    }
    public boolean isTerminal()              { return currentState.isTerminal(); }

    public synchronized List<StateTransitionEvent<PipelineState>> getHistory() {
        return List.copyOf(history);
    }
}