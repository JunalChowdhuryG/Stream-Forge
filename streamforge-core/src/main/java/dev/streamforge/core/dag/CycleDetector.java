package dev.streamforge.core.dag;

import java.util.*;

/**
 * Detecta ciclos en un grafo de nodos usando DFS con coloracion de nodos
 * Estados de coloracion:
 *   WHITE (0) - no visitado
 *   GRAY  (1) - en proceso (en el stack actual de DFS)
 *   BLACK (2) - completamente procesado
 * Un ciclo existe cuando DFS encuentra una arista hacia un nodo GRAY
 * (back edge), lo que significa que encontramos un camino de vuelta
 * a un nodo que ya esta en el stack de llamadas actual
 */
public class CycleDetector {

    private static final int WHITE = 0;
    private static final int GRAY  = 1;
    private static final int BLACK = 2;

    /**
     * Busca el primer ciclo en el conjunto de nodos dado
     * @param nodes mapa de stepId a DAGNode
     * @return lista con los stepIds que forman el ciclo, o lista vacia si no hay ciclo
     */
    public List<String> findCycle(Map<String, DAGNode> nodes) {
        Map<String, Integer> color  = new HashMap<>();
        Map<String, String>  parent = new HashMap<>();

        for (String id : nodes.keySet()) {
            color.put(id, WHITE);
        }

        for (String id : nodes.keySet()) {
            if (color.get(id) == WHITE) {
                List<String> cycle = new ArrayList<>();
                if (dfs(id, nodes, color, parent, cycle)) {
                    return cycle;
                }
            }
        }

        return List.of(); // sin ciclos
    }

    private boolean dfs(String nodeId,
                        Map<String, DAGNode> nodes,
                        Map<String, Integer> color,
                        Map<String, String> parent,
                        List<String> cycle) {
        color.put(nodeId, GRAY);

        DAGNode node = nodes.get(nodeId);
        for (DAGNode successor : node.getSuccessors()) {
            String successorId = successor.getStepId();

            if (color.get(successorId) == GRAY) {
                // Ciclo encontrado - reconstruir el camino
                reconstructCycle(nodeId, successorId, parent, cycle);
                return true;
            }

            if (color.get(successorId) == WHITE) {
                parent.put(successorId, nodeId);
                if (dfs(successorId, nodes, color, parent, cycle)) {
                    return true;
                }
            }
        }

        color.put(nodeId, BLACK);
        return false;
    }

    private void reconstructCycle(String from, String cycleStart,
                                   Map<String, String> parent,
                                   List<String> cycle) {
        // Reconstruir desde 'from' hacia atras hasta llegar a cycleStart
        List<String> path = new ArrayList<>();
        path.add(from);

        String current = from;
        while (!current.equals(cycleStart)) {
            current = parent.get(current);
            if (current == null) break;
            path.add(current);
        }
        path.add(cycleStart);

        Collections.reverse(path);
        cycle.addAll(path);
    }
}