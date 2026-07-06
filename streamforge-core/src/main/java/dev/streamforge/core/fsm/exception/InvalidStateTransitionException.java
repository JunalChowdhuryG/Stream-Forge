package dev.streamforge.core.fsm.exception;

/**
 * Lanzada cuando se intenta una transicion de estado invalida
 * en PipelineFSM o StepFSM
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String fsmType,
                                           Object from,
                                           Object to,
                                           Object validTransitions) {
        super(String.format(
            "[%s] Transicion invalida: %s -> %s. Transiciones validas: %s",
            fsmType, from, to, validTransitions
        ));
    }
}