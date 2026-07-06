package dev.streamforge.core.dag;

import dev.streamforge.core.dag.exception.UnknownDependencyException;
import dev.streamforge.core.model.PipelineDefinition;
import dev.streamforge.core.model.StepDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construye el DAG de ejecucion a partir de una PipelineDefinition
 * Responsabilidades:
 *   1. Crear un DAGNode por cada paso del pipeline
 *   2. Conectar nodos segun las dependencias declaradas en depends_on
 *   3. Validar que todas las dependencias referencian pasos existentes
 * No detecta ciclos - esa responsabilidad es del TopologicalSorter
 */
public class DAGBuilder {

    private static final Logger log = LoggerFactory.getLogger(DAGBuilder.class);

    /**
     * Construye el DAG para el pipeline dado
     *
     * @param pipeline definicion del pipeline leida desde YAML
     * @return PipelineDAG con nodos conectados
     * @throws UnknownDependencyException si algun depends_on referencia un paso inexistente
     */
    public PipelineDAG build(PipelineDefinition pipeline) {
        log.info("Construyendo DAG para pipeline '{}' con {} pasos",
                pipeline.getId(), pipeline.getSteps().size());

        // Paso 1: crear un nodo por cada paso
        Map<String, DAGNode> nodes = new LinkedHashMap<>();
        for (StepDefinition step : pipeline.getSteps()) {
            nodes.put(step.getId(), new DAGNode(step));
        }

        // Paso 2: conectar nodos segun depends_on
        for (StepDefinition step : pipeline.getSteps()) {
            DAGNode currentNode = nodes.get(step.getId());

            for (String dependencyId : step.getDependsOn()) {
                DAGNode dependencyNode = nodes.get(dependencyId);

                if (dependencyNode == null) {
                    throw new UnknownDependencyException(step.getId(), dependencyId);
                }

                // dependencyNode -> currentNode
                dependencyNode.addSuccessor(currentNode);
                currentNode.addPredecessor(dependencyNode);

                log.debug("Arista: {} -> {}", dependencyId, step.getId());
            }
        }

        PipelineDAG dag = new PipelineDAG(pipeline.getId(), nodes);

        log.info("DAG construido - {} nodos, {} nodos raiz",
                dag.getNodeCount(), dag.getRootNodes().size());

        return dag;
    }
}