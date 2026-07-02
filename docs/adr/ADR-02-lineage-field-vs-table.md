# ADR-02 — Linaje a nivel de campo vs nivel de tabla

**Estado:** Aceptado | **Fecha:** 2026

## Contexto

El linaje a nivel de tabla (dataset A → dataset B) es mas simple de implementar.

## Decision

Linaje a nivel de campo: cada arista conecta campos especificos.

## Consecuencias

La consulta downstream() responde exactamente que campos se ven afectados
al cambiar un campo especifico. A nivel de tabla solo diria "el dataset B depende del A".

## Alternativa descartada

Linaje a nivel de tabla. Util para auditoria de alto nivel pero insuficiente
para responder "que reportes se rompen si cambio este campo".