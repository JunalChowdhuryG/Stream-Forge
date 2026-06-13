package dev.streamforge.core.dag;

import java.util.*;

/**
 * Representacion del DAG completo de un pipeline
 * Contiene todos los nodos y la conectividad entre ellos
 * Inmutable una vez construido por el DAGBuilder
 */
public class PipelineDAG {

    private final String pipelineId;
    private final Map<String, DAGNode> nodes;

    public PipelineDAG(String pipelineId, Map<String, DAGNode> nodes) {
        this.pipelineId = pipelineId;
        this.nodes      = Map.copyOf(nodes);
    }

    public String getPipelineId()            { return pipelineId;              }
    public Map<String, DAGNode> getNodes()   { return nodes;                   }
    public int getNodeCount()                { return nodes.size();            }
    public Optional<DAGNode> getNode(String stepId) {
        return Optional.ofNullable(nodes.get(stepId));
    }

    /**
     * Retorna los nodos sin predecesores (puntos de entrada del DAG)
     */
    public List<DAGNode> getRootNodes() {
        return nodes.values().stream()
                .filter(n -> n.getInDegree() == 0)
                .toList();
    }

    @Override
    public String toString() {
        return "PipelineDAG{pipelineId='" + pipelineId + "', nodes=" + nodes.size() + "}";
    }
}