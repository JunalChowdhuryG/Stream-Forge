package dev.streamforge.core.lineage;

/**
 * Tipo de transformacion que genera una arista en el grafo de linaje
 * Cada tipo tiene semantica distinta sobre como los campos de entrada
 * se mapean a campos de salida
 */
public enum TransformationType {
    SOURCE,      // lectura desde fuente externa — no tiene campos de entrada
    JOIN,        // combina campos de dos datasets
    FILTER,      // mismos campos que el input, solo cambia el numero de filas
    AGGREGATE,   // reduce filas: group by + funcion de agregacion
    SELECT,      // proyeccion o renombrado de campos
    SINK,        // escritura a destino externo
    CUSTOM       // transformacion personalizada
}