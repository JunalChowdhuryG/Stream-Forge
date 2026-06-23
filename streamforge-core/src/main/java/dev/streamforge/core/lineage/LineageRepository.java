package dev.streamforge.core.lineage;

import java.util.List;

/**
 * Interfaz para persistencia del grafo de linaje.
 * La implementacion concreta vive en streamforge-engine.
 */
public interface LineageRepository {

    /**
     * Persiste un nodo y retorna el nodo con su ID asignado por la BD.
     */
    LineageNode saveNode(LineageNode node);

    /**
     * Persiste una arista. Los nodos deben tener ID asignado previamente.
     */
    void saveEdge(LineageEdge edge);

    /**
     * Persiste el grafo completo de una ejecucion.
     */
    void saveGraph(LineageGraph graph);

    /**
     * Carga todos los nodos de una ejecucion.
     */
    List<LineageNode> findNodesByExecution(String executionId);

    /**
     * Carga todas las aristas de una ejecucion.
     */
    List<LineageEdge> findEdgesByExecution(String executionId);

    /**
     * Implementacion nula para tests sin base de datos.
     */
    LineageRepository NOOP = new LineageRepository() {
        public LineageNode saveNode(LineageNode n)      { return n;        }
        public void saveEdge(LineageEdge e)             {}
        public void saveGraph(LineageGraph g)           {}
        public List<LineageNode> findNodesByExecution(String e) { return List.of(); }
        public List<LineageEdge> findEdgesByExecution(String e) { return List.of(); }
    };
}