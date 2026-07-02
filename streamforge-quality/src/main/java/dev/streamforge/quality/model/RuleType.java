package dev.streamforge.quality.model;

/**
 * Tipos de reglas de calidad soportadas por el motor.
 */
public enum RuleType {
    NOT_NULL,       // el campo no tiene valores nulos
    RANGE,          // el valor numerico esta en [min, max]
    UNIQUE,         // no hay duplicados en el campo dentro del batch
    REGEX,          // el valor cumple la expresion regular
    FRESHNESS,      // el timestamp no es mas antiguo que max_age_hours
    REFERENTIAL,    // el valor existe en una tabla de referencia
    DISTRIBUTION    // la distribucion estadistica no se ha desviado
}