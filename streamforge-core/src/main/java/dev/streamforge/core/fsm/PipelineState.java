package dev.streamforge.core.fsm;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados del ciclo de vida de una ejecucion de pipeline
 * Matriz de transiciones validas:
 *   CREATED   -> VALIDATED
 *   VALIDATED -> RUNNING
 *   RUNNING   -> COMPLETED, FAILED
 *   FAILED    -> RESUMING
 *   RESUMING  -> RUNNING
 *   COMPLETED -> (terminal)
 */
public enum PipelineState {

    CREATED {
        @Override public Set<PipelineState> validTransitions() {
            return EnumSet.of(VALIDATED);
        }
    },
    VALIDATED {
        @Override public Set<PipelineState> validTransitions() {
            return EnumSet.of(RUNNING);
        }
    },
    RUNNING {
        @Override public Set<PipelineState> validTransitions() {
            return EnumSet.of(COMPLETED, FAILED);
        }
    },
    FAILED {
        @Override public Set<PipelineState> validTransitions() {
            return EnumSet.of(RESUMING);
        }
    },
    RESUMING {
        @Override public Set<PipelineState> validTransitions() {
            return EnumSet.of(RUNNING);
        }
    },
    COMPLETED {
        @Override public Set<PipelineState> validTransitions() {
            return EnumSet.noneOf(PipelineState.class);
        }
        @Override public boolean isTerminal() { return true; }
    };

    public abstract Set<PipelineState> validTransitions();

    public boolean isTerminal() { return false; }

    public boolean canTransitionTo(PipelineState target) {
        return validTransitions().contains(target);
    }
}