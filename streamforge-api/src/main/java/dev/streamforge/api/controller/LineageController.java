package dev.streamforge.api.controller;

import dev.streamforge.api.dto.LineageResponse;
import dev.streamforge.core.lineage.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;

/**
 * API REST para consultas de linaje de datos.
 *
 * GET /lineage/impact/{dataset}/{field} - impacto inverso (downstream)
 * GET /lineage/origin/{dataset}/{field} - origen de un campo (upstream)
 * GET /executions/{execId}/lineage      - grafo completo de una ejecucion
 */
@RestController
public class LineageController {

    private final LineageRepository lineageRepository;

    public LineageController(LineageRepository lineageRepository) {
        this.lineageRepository = lineageRepository;
    }

    @GetMapping("/lineage/impact/{dataset}/{field}")
    public ResponseEntity<LineageResponse> getImpact(
            @PathVariable String dataset,
            @PathVariable String field,
            @RequestParam(required = false) String executionId) {

        if (executionId == null) {
            return ResponseEntity.badRequest().build();
        }

        // Reconstruir grafo desde el repositorio
        List<LineageNode> nodes = lineageRepository.findNodesByExecution(executionId);
        List<LineageEdge> edges = lineageRepository.findEdgesByExecution(executionId);

        LineageGraph graph = reconstructGraph(executionId, nodes, edges);
        List<LineageNode> downstream = graph.downstream(dataset, field);

        List<LineageResponse.FieldNode> fieldNodes = downstream.stream()
            .map(n -> new LineageResponse.FieldNode(
                n.getDatasetName(), n.getFieldName(),
                n.getDataType(), n.getStepId()
            )).toList();

        return ResponseEntity.ok(new LineageResponse(
            executionId, dataset, field, "DOWNSTREAM", fieldNodes
        ));
    }

    @GetMapping("/lineage/origin/{dataset}/{field}")
    public ResponseEntity<LineageResponse> getOrigin(
            @PathVariable String dataset,
            @PathVariable String field,
            @RequestParam(required = false) String executionId) {

        if (executionId == null) {
            return ResponseEntity.badRequest().build();
        }

        List<LineageNode> nodes = lineageRepository.findNodesByExecution(executionId);
        List<LineageEdge> edges = lineageRepository.findEdgesByExecution(executionId);

        LineageGraph graph    = reconstructGraph(executionId, nodes, edges);
        List<LineageNode> upstream = graph.upstream(dataset, field);

        List<LineageResponse.FieldNode> fieldNodes = upstream.stream()
            .map(n -> new LineageResponse.FieldNode(
                n.getDatasetName(), n.getFieldName(),
                n.getDataType(), n.getStepId()
            )).toList();

        return ResponseEntity.ok(new LineageResponse(
            executionId, dataset, field, "UPSTREAM", fieldNodes
        ));
    }

    @GetMapping("/executions/{execId}/lineage")
    public ResponseEntity<Map<String, Object>> getExecutionLineage(
            @PathVariable String execId) {

        List<LineageNode> nodes = lineageRepository.findNodesByExecution(execId);
        List<LineageEdge> edges = lineageRepository.findEdgesByExecution(execId);

        return ResponseEntity.ok(java.util.Map.of(
            "executionId", execId,
            "nodeCount",   nodes.size(),
            "edgeCount",   edges.size(),
            "nodes", nodes.stream().map(n -> java.util.Map.of(
                "dataset", n.getDatasetName(),
                "field",   n.getFieldName(),
                "type",    n.getDataType() != null ? n.getDataType() : "UNKNOWN",
                "step",    n.getStepId() != null   ? n.getStepId()   : ""
            )).toList()
        ));
    }

    private LineageGraph reconstructGraph(String executionId,
                                           List<LineageNode> nodes,
                                           List<LineageEdge> edges) {
        LineageGraph graph = new LineageGraph(executionId);
        for (LineageNode n : nodes) {
            graph.addNode(n.getDatasetName(), n.getFieldName(),
                          n.getDataType(), n.getStepId());
        }
        // Reconstruir aristas
        java.util.Map<Long, LineageNode> byId = new java.util.HashMap<>();
        for (LineageNode n : nodes) {
            if (n.getId() != null) byId.put(n.getId(), n);
        }
        for (LineageEdge e : edges) {
            LineageNode src = graph.getNode(
                e.getSource().getDatasetName(),
                e.getSource().getFieldName()).orElse(null);
            LineageNode tgt = graph.getNode(
                e.getTarget().getDatasetName(),
                e.getTarget().getFieldName()).orElse(null);
            if (src != null && tgt != null) {
                graph.addEdge(src, tgt, e.getTransformationType(), e.getStepId());
            }
        }
        return graph;
    }
}