package dev.streamforge.core.lineage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grafo dirigido de linaje de datos para una ejecucion de pipeline.
 *
 * Cada nodo es un campo (dataset:campo) y cada arista es la transformacion
 * que lo produjo. El grafo crece a medida que los pasos del pipeline ejecutan.
 *
 * Soporta dos consultas clave:
 *   - upstream(campo):  de donde vienen los datos de este campo
 *   - downstream(campo): que campos se ven afectados si este campo cambia
 *
 * Thread-safe para uso concurrente desde pasos paralelos.
 */
public class LineageGraph {

    private static final Logger log = LoggerFactory.getLogger(LineageGraph.class);

    private final String executionId;
    private final ConcurrentHashMap<String, LineageNode> nodes;
    private final List<LineageEdge> edges;

    public LineageGraph(String executionId) {
        this.executionId = executionId;
        this.nodes       = new ConcurrentHashMap<>();
        this.edges       = Collections.synchronizedList(new ArrayList<>());
    }

    //Registro de nodos y aristas
    /**
     * Agrega un nodo al grafo. Si ya existe, retorna el existente.
     */
    public synchronized LineageNode addNode(String datasetName, String fieldName,
                                             String dataType, String stepId) {
        String key = datasetName + ":" + fieldName;
        return nodes.computeIfAbsent(key, k ->
            new LineageNode(executionId, datasetName, fieldName, dataType, stepId)
        );
    }

    /**
     * Agrega una arista dirigida source -> target.
     */
    public synchronized void addEdge(LineageNode source, LineageNode target,
                                      TransformationType type, String stepId) {
        edges.add(new LineageEdge(executionId, source, target, type, stepId));
        log.debug("Linaje registrado: {} -> {} [{}]",
                source.nodeKey(), target.nodeKey(), type);
    }

    //Consultas

    /**
     * Retorna todos los campos que contribuyeron (directa o indirectamente)
     * al campo dado. BFS hacia atras en el grafo.
     *
     * @param datasetName nombre del dataset
     * @param fieldName   nombre del campo
     * @return lista de nodos upstream en orden BFS
     */
    public List<LineageNode> upstream(String datasetName, String fieldName) {
        String targetKey = datasetName + ":" + fieldName;
        LineageNode start = nodes.get(targetKey);
        if (start == null) return List.of();

        List<LineageNode> result  = new ArrayList<>();
        Queue<LineageNode> queue  = new LinkedList<>();
        Set<String>        visited = new HashSet<>();

        queue.add(start);
        visited.add(targetKey);

        while (!queue.isEmpty()) {
            LineageNode current = queue.poll();

            // Buscar aristas cuyo TARGET es el nodo actual
            for (LineageEdge edge : edges) {
                if (edge.getTarget().nodeKey().equals(current.nodeKey())) {
                    LineageNode source = edge.getSource();
                    if (!visited.contains(source.nodeKey())) {
                        visited.add(source.nodeKey());
                        result.add(source);
                        queue.add(source);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Retorna todos los campos que se ven afectados si el campo dado cambia.
     * BFS hacia adelante en el grafo — consulta de impacto inverso.
     *
     * @param datasetName nombre del dataset
     * @param fieldName   nombre del campo
     * @return lista de nodos downstream en orden BFS
     */
    public List<LineageNode> downstream(String datasetName, String fieldName) {
        String sourceKey = datasetName + ":" + fieldName;
        LineageNode start = nodes.get(sourceKey);
        if (start == null) return List.of();

        List<LineageNode> result   = new ArrayList<>();
        Queue<LineageNode> queue   = new LinkedList<>();
        Set<String>        visited = new HashSet<>();

        queue.add(start);
        visited.add(sourceKey);

        while (!queue.isEmpty()) {
            LineageNode current = queue.poll();

            // Buscar aristas cuyo SOURCE es el nodo actual
            for (LineageEdge edge : edges) {
                if (edge.getSource().nodeKey().equals(current.nodeKey())) {
                    LineageNode target = edge.getTarget();
                    if (!visited.contains(target.nodeKey())) {
                        visited.add(target.nodeKey());
                        result.add(target);
                        queue.add(target);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Retorna los nodos de origen (sin predecesores) del grafo.
     */
    public List<LineageNode> getSources() {
        Set<String> targetsInEdges = new HashSet<>();
        for (LineageEdge e : edges) {
            targetsInEdges.add(e.getTarget().nodeKey());
        }
        return nodes.values().stream()
                .filter(n -> !targetsInEdges.contains(n.nodeKey()))
                .toList();
    }

    public String getExecutionId()           { return executionId;             }
    public Collection<LineageNode> getNodes(){ return Collections.unmodifiableCollection(nodes.values()); }
    public List<LineageEdge> getEdges()      { return Collections.unmodifiableList(edges); }
    public int getNodeCount()                { return nodes.size();            }
    public int getEdgeCount()                { return edges.size();            }
    public Optional<LineageNode> getNode(String dataset, String field) {
        return Optional.ofNullable(nodes.get(dataset + ":" + field));
    }
}