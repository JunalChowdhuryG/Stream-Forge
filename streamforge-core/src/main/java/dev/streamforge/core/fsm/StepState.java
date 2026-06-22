package dev.streamforge.core.fsm;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados del ciclo de vida de la ejecucion de un paso individual
 * Matriz de transiciones validas:
 *
 *   PENDING  -> RUNNING, SKIPPED
 *   RUNNING  -> COMPLETED, FAILED
 *   SKIPPED  -> (terminal - checkpoint existente, no se re-ejecuta)
 *   COMPLETED-> (terminal)
 *   FAILED   -> (terminal)
 */
public enum StepState {

    PENDING {
        @Override public Set<StepState> validTransitions() {
            return EnumSet.of(RUNNING, SKIPPED);
        }
    },
    RUNNING {
        @Override public Set<StepState> validTransitions() {
            return EnumSet.of(COMPLETED, FAILED);
        }
    },
    SKIPPED {
        @Override public Set<StepState> validTransitions() {
            return EnumSet.noneOf(StepState.class);
        }
        @Override public boolean isTerminal() { return true; }
    },
    COMPLETED {
        @Override public Set<StepState> validTransitions() {
            return EnumSet.noneOf(StepState.class);
        }
        @Override public boolean isTerminal() { return true; }
    },
    FAILED {
        @Override public Set<StepState> validTransitions() {
            return EnumSet.noneOf(StepState.class);
        }
        @Override public boolean isTerminal() { return true; }
    };

    public abstract Set<StepState> validTransitions();

    public boolean isTerminal()                       { return false; }
    public boolean isSuccess()                        { return this == COMPLETED || this == SKIPPED; }
    public boolean canTransitionTo(StepState target)  { return validTransitions().contains(target); }
}