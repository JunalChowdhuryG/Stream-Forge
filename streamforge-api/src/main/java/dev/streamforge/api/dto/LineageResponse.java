package dev.streamforge.api.dto;

import java.util.List;

/**
 * Respuesta de la API para consultas de linaje.
 */
public record LineageResponse(
    String executionId,
    String dataset,
    String field,
    String direction,
    List<FieldNode> nodes
) {
    public record FieldNode(
        String dataset,
        String field,
        String dataType,
        String stepId
    ) {}
}