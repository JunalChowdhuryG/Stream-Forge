# ADR-01 — DAG vs ejecucion secuencial simple

**Estado:** Aceptado | **Fecha:** 2026

## Contexto

La mayoria de los pipelines podrian ejecutarse en secuencia simple.
¿Vale la complejidad de un DAG?

## Decision

DAG con ordenamiento topologico (Kahn) y ejecucion paralela de pasos independientes.

## Consecuencias

Pasos independientes (load-warehouse y load-kafka en el demo) corren en paralelo
reduciendo el tiempo total. El DAG documenta las dependencias explicitamente en YAML.

## Alternativa descartada

Ejecucion secuencial. No aprovecha el paralelismo y obliga al desarrollador
a ordenar manualmente los pasos, lo que puede introducir errores de orden.