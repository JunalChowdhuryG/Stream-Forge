package dev.streamforge.core.lineage;

import dev.streamforge.core.model.DataBatch;
import dev.streamforge.core.model.FieldDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registra el linaje de datos en el LineageGraph segun el tipo de transformacion.
 *
 * Cada tipo de transformacion tiene reglas distintas sobre como los campos
 * de entrada se mapean a campos de salida:
 *
 *   SOURCE    -> los campos del output no tienen predecessores en el grafo
 *   FILTER    -> todos los campos del output vienen directamente del input
 *   JOIN      -> cada campo del output viene de uno de los dos inputs
 *   AGGREGATE -> campos de grupo vienen del input, campos agregados son nuevos
 *   SELECT    -> mapeo explicito campo a campo con posibles renombrados
 *   SINK      -> los campos del input van al destino (nodo terminal)
 */
public class LineageRecorder {

    private static final Logger log = LoggerFactory.getLogger(LineageRecorder.class);

    private final LineageGraph graph;

    public LineageRecorder(LineageGraph graph) {
        this.graph = graph;
    }

    /**
     * Registra el linaje de una fuente (SOURCE).
     * Los campos del output se crean como nodos raiz sin predecesores.
     */
    public void recordSource(DataBatch output, String stepId) {
        log.debug("Registrando linaje SOURCE — dataset={}, step={}",
                output.getDatasetName(), stepId);

        for (FieldDefinition field : output.getSchema().getFields()) {
            graph.addNode(
                output.getDatasetName(),
                field.getName(),
                field.getType().name(),
                stepId
            );
        }
    }

    /**
     * Registra el linaje de un FILTER.
     * Todos los campos del output vienen directamente del input (mismos campos).
     */
    public void recordFilter(DataBatch input, DataBatch output, String stepId) {
        log.debug("Registrando linaje FILTER — {} -> {}, step={}",
                input.getDatasetName(), output.getDatasetName(), stepId);

        for (FieldDefinition field : output.getSchema().getFields()) {
            LineageNode sourceNode = graph.addNode(
                input.getDatasetName(), field.getName(),
                field.getType().name(), stepId
            );
            LineageNode targetNode = graph.addNode(
                output.getDatasetName(), field.getName(),
                field.getType().name(), stepId
            );
            graph.addEdge(sourceNode, targetNode, TransformationType.FILTER, stepId);
        }
    }

    /**
     * Registra el linaje de un JOIN.
     * Cada campo del output se origina en uno de los dos inputs.
     * Si el campo existe en ambos inputs, se registran ambas aristas.
     */
    public void recordJoin(DataBatch left, DataBatch right,
                            DataBatch output, String stepId) {
        log.debug("Registrando linaje JOIN — {}, {} -> {}, step={}",
                left.getDatasetName(), right.getDatasetName(),
                output.getDatasetName(), stepId);

        for (FieldDefinition outField : output.getSchema().getFields()) {
            LineageNode targetNode = graph.addNode(
                output.getDatasetName(), outField.getName(),
                outField.getType().name(), stepId
            );

            // Buscar el campo en left
            left.getSchema().getField(outField.getName()).ifPresent(leftField -> {
                LineageNode sourceNode = graph.addNode(
                    left.getDatasetName(), leftField.getName(),
                    leftField.getType().name(), stepId
                );
                graph.addEdge(sourceNode, targetNode, TransformationType.JOIN, stepId);
            });

            // Buscar el campo en right
            right.getSchema().getField(outField.getName()).ifPresent(rightField -> {
                LineageNode sourceNode = graph.addNode(
                    right.getDatasetName(), rightField.getName(),
                    rightField.getType().name(), stepId
                );
                graph.addEdge(sourceNode, targetNode, TransformationType.JOIN, stepId);
            });
        }
    }

    /**
     * Registra el linaje de una escritura (SINK).
     * Los campos del input se conectan con campos del destino.
     */
    public void recordSink(DataBatch input, String destinationDataset,
                            String stepId) {
        log.debug("Registrando linaje SINK — {} -> {}, step={}",
                input.getDatasetName(), destinationDataset, stepId);

        for (FieldDefinition field : input.getSchema().getFields()) {
            LineageNode sourceNode = graph.addNode(
                input.getDatasetName(), field.getName(),
                field.getType().name(), stepId
            );
            LineageNode targetNode = graph.addNode(
                destinationDataset, field.getName(),
                field.getType().name(), stepId
            );
            graph.addEdge(sourceNode, targetNode, TransformationType.SINK, stepId);
        }
    }

    /**
     * Registra el linaje de un SELECT con mapeo explicito campo a campo.
     * El mapa fieldMapping es: nombreCampoOrigen -> nombreCampoDestino.
     */
    public void recordSelect(DataBatch input, DataBatch output,
                              java.util.Map<String, String> fieldMapping,
                              String stepId) {
        log.debug("Registrando linaje SELECT — {} -> {}, step={}",
                input.getDatasetName(), output.getDatasetName(), stepId);

        for (java.util.Map.Entry<String, String> mapping : fieldMapping.entrySet()) {
            String sourceFieldName = mapping.getKey();
            String targetFieldName = mapping.getValue();

            input.getSchema().getField(sourceFieldName).ifPresent(sourceField -> {
                output.getSchema().getField(targetFieldName).ifPresent(targetField -> {
                    LineageNode sourceNode = graph.addNode(
                        input.getDatasetName(), sourceField.getName(),
                        sourceField.getType().name(), stepId
                    );
                    LineageNode targetNode = graph.addNode(
                        output.getDatasetName(), targetField.getName(),
                        targetField.getType().name(), stepId
                    );
                    graph.addEdge(sourceNode, targetNode, TransformationType.SELECT, stepId);
                });
            });
        }
    }

    public LineageGraph getGraph() { return graph; }
}