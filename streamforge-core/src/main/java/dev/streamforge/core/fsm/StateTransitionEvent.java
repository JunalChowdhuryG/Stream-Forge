package dev.streamforge.core.fsm;

import java.time.Instant;

/**
 * Evento de transicion de estado registrado en el historial del FSM
 */
public record StateTransitionEvent<S>(
    String entityId,
    S from,
    S to,
    Instant occurredAt
) {}