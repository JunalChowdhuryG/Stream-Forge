# ADR-03 — Checkpointing por etapa vs transaccion global

**Estado:** Aceptado | **Fecha:** 2026

## Contexto

Una transaccion global que hace rollback completo ante cualquier fallo
seria mas simple conceptualmente.

## Decision

Checkpointing por etapa con reanudacion desde el punto de falla.

## Consecuencias

Un pipeline de 12 pasos que falla en el paso 11 reanuda desde el paso 11.
Para pipelines que procesan horas de datos esto es critico.

## Alternativa descartada

Transaccion global. En pipelines de datos los pasos intermedios pueden escribir
a sistemas externos (Kafka, APIs) que no participan en transacciones ACID.
El rollback global es imposible en la practica.