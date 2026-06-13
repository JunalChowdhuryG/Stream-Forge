package dev.streamforge.core.dag;

import dev.streamforge.core.dag.exception.CyclicDependencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implementa el algoritmo de Kahn para ordenamiento topologico del DAG
 * Produce niveles de ejecucion: cada nivel contiene los pasos que pueden
 * ejecutarse en paralelo porque no tienen dependencias entre si y todas
 * sus dependencias del nivel anterior ya completaron
 * Detecta ciclos como efecto secundario del algoritmo:
 * si al terminar hay nodos sin procesar, existe un ciclo
 */
public class TopologicalSorter {

    private static final Logger log = LoggerFactory.getLogger(TopologicalSorter.class);

    private final CycleDetector cycleDetector = new CycleDetector();

    /**
     * Resultado del ordenamiento topologico
     * Contiene los niveles de ejecucion en orden
     */
    public record SortResult(
        List<List<DAGNode>> levels,  // cada lista es un nivel de paralelismo
        List<DAGNode> flatOrder      // orden plano para ejecucion secuencial
    ) {
        public int getLevelCount()  { return levels.size(); }
        public int getTotalNodes()  { return flatOrder.size(); }
    }

    /**
     * Ejecuta el ordenamiento topologico sobre el DAG
     * @param dag el DAG construido por DAGBuilder
     * @return SortResult con niveles de paralelismo y orden plano
     * @throws CyclicDependencyException si se detecta un ciclo
     */
    public SortResult sort(PipelineDAG dag) {
        log.info("Iniciando ordenamiento topologico para pipeline '{}'", dag.getPipelineId());

        // Trabajar sobre copias del in-degree para no mutar el DAG original
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (Map.Entry<String, DAGNode> entry : dag.getNodes().entrySet()) {
            inDegree.put(entry.getKey(), entry.getValue().getInDegree());
        }

        List<List<DAGNode>> levels   = new ArrayList<>();
        List<DAGNode>       flatOrder = new ArrayList<>();
        int processedCount = 0;

        // Cola inicial: todos los nodos con in-degree 0
        Queue<DAGNode> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(dag.getNodes().get(entry.getKey()));
            }
        }

        while (!queue.isEmpty()) {
            // Todos los nodos en la cola forman el nivel actual de paralelismo
            List<DAGNode> currentLevel = new ArrayList<>(queue);
            queue.clear();

            log.debug("Nivel {}: {}", levels.size(),
                    currentLevel.stream().map(DAGNode::getStepId).toList());

            levels.add(currentLevel);
            flatOrder.addAll(currentLevel);
            processedCount += currentLevel.size();

            // Decrementar in-degree de los sucesores
            for (DAGNode node : currentLevel) {
                for (DAGNode successor : node.getSuccessors()) {
                    int newDegree = inDegree.merge(successor.getStepId(), -1, Integer::sum);
                    if (newDegree == 0) {
                        queue.add(successor);
                    }
                }
            }
        }

        // Si no procesamos todos los nodos, hay un ciclo
        if (processedCount < dag.getNodeCount()) {
            List<String> cycle = cycleDetector.findCycle(dag.getNodes());
            log.error("Ciclo detectado en pipeline '{}': {}", dag.getPipelineId(), cycle);
            throw new CyclicDependencyException(cycle);
        }

        log.info("Ordenamiento completado - {} niveles, {} nodos totales",
                levels.size(), processedCount);

        return new SortResult(levels, flatOrder);
    }
}