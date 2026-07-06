package dev.streamforge.core.fsm;

import dev.streamforge.core.fsm.exception.InvalidStateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maquina de estados para el ciclo de vida de un paso individual
 * Cada paso en una ejecucion de pipeline tiene su propio StepFSM
 * El estado SKIPPED se activa cuando existe un checkpoint valido
 * del paso de una ejecucion anterior - no se re-ejecuta
 */
public class StepFSM {

    private static final Logger log = LoggerFactory.getLogger(StepFSM.class);

    private final String executionId;
    private final String stepId;
    private volatile StepState currentState;
    private final List<StateTransitionEvent<StepState>> history;

    public StepFSM(String executionId, String stepId) {
        this.executionId  = executionId;
        this.stepId       = stepId;
        this.currentState = StepState.PENDING;
        this.history      = new ArrayList<>();

        history.add(new StateTransitionEvent<>(stepId, null, StepState.PENDING, Instant.now()));
    }

    public synchronized void transition(StepState target) {
        if (!currentState.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                "StepFSM[" + stepId + "]", currentState, target, currentState.validTransitions()
            );
        }

        StepState previous = currentState;
        currentState = target;
        history.add(new StateTransitionEvent<>(stepId, previous, target, Instant.now()));

        log.debug("Step FSM - executionId={}, step={}, {} -> {}",
                executionId, stepId, previous, target);
    }

    public void run()     { transition(StepState.RUNNING);   }
    public void complete(){ transition(StepState.COMPLETED); }
    public void fail()    { transition(StepState.FAILED);    }
    public void skip()    { transition(StepState.SKIPPED);   }

    public StepState getCurrentState()   { return currentState; }
    public String getExecutionId()       { return executionId;  }
    public String getStepId()            { return stepId;       }
    public boolean isTerminal()          { return currentState.isTerminal(); }
    public boolean isSuccess()           { return currentState.isSuccess();  }

    public synchronized List<StateTransitionEvent<StepState>> getHistory() {
        return List.copyOf(history);
    }
}