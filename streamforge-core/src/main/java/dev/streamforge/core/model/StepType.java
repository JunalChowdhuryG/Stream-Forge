package dev.streamforge.core.model;

/**
 * Tipo de paso en un pipeline
 * SOURCE   - lee datos de una fuente externa
 * TRANSFORM - transforma datos ya en memoria
 * SINK     - escribe datos a un destino externo
 */
public enum StepType {
    SOURCE,
    TRANSFORM,
    SINK
}