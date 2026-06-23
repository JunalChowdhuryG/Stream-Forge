package dev.streamforge.core.lineage;

import dev.streamforge.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LineageGraph — registro y consulta de linaje")
class LineageGraphTest {

    private static final String EXEC_ID = "exec-lineage-test";
    private LineageGraph graph;
    private LineageRecorder recorder;

    @BeforeEach
    void setUp() {
        graph    = new LineageGraph(EXEC_ID);
        recorder = new LineageRecorder(graph);
    }

    @Test
    @DisplayName("registrar SOURCE crea nodos sin predecesores")
    void source_creaNodosSinPredecesores() {
        DataBatch batch = buildBatch("orders_raw",
            field("order_id",    FieldType.LONG),
            field("customer_id", FieldType.LONG),
            field("total",       FieldType.DOUBLE)
        );

        recorder.recordSource(batch, "extract-orders");

        assertEquals(3, graph.getNodeCount());
        assertEquals(0, graph.getEdgeCount());
        assertTrue(graph.getNode("orders_raw", "order_id").isPresent());
    }

    @Test
    @DisplayName("registrar FILTER crea aristas campo a campo")
    void filter_creaAristasCampoACampo() {
        DataBatch input = buildBatch("orders_raw",
            field("order_id", FieldType.LONG),
            field("status",   FieldType.STRING)
        );
        DataBatch output = buildBatch("orders_valid",
            field("order_id", FieldType.LONG),
            field("status",   FieldType.STRING)
        );

        recorder.recordSource(input, "extract");
        recorder.recordFilter(input, output, "filter-valid");

        assertEquals(4, graph.getNodeCount()); // 2 input + 2 output
        assertEquals(2, graph.getEdgeCount()); // una arista por campo
    }

    @Test
    @DisplayName("registrar JOIN conecta campos de ambos inputs con el output")
    void join_conectaCamposDeAmbosInputs() {
        DataBatch left = buildBatch("orders_raw",
            field("customer_id", FieldType.LONG),
            field("total",       FieldType.DOUBLE)
        );
        DataBatch right = buildBatch("customers_raw",
            field("customer_id", FieldType.LONG),
            field("name",        FieldType.STRING)
        );
        DataBatch output = buildBatch("orders_enriched",
            field("customer_id", FieldType.LONG),
            field("total",       FieldType.DOUBLE),
            field("name",        FieldType.STRING)
        );

        recorder.recordSource(left,  "extract-orders");
        recorder.recordSource(right, "extract-customers");
        recorder.recordJoin(left, right, output, "join-step");

        // customer_id tiene aristas desde ambos inputs (2 aristas)
        // total viene solo de left (1 arista)
        // name viene solo de right (1 arista)
        assertEquals(4, graph.getEdgeCount());
    }

    @Test
    @DisplayName("consulta downstream retorna todos los campos afectados")
    void downstream_retornaCamposAfectados() {
        // Simular pipeline: orders_raw -> orders_enriched -> orders_valid -> warehouse
        DataBatch raw      = buildBatch("orders_raw",
            field("customer_id", FieldType.LONG));
        DataBatch enriched = buildBatch("orders_enriched",
            field("customer_id", FieldType.LONG));
        DataBatch valid    = buildBatch("orders_valid",
            field("customer_id", FieldType.LONG));

        recorder.recordSource(raw, "extract");
        recorder.recordFilter(raw, enriched, "join");
        recorder.recordFilter(enriched, valid, "filter");
        recorder.recordSink(valid, "warehouse", "load");

        // Desde orders_raw:customer_id -> debe llegar hasta warehouse
        List<LineageNode> downstream =
            graph.downstream("orders_raw", "customer_id");

        assertFalse(downstream.isEmpty());
        assertTrue(downstream.stream()
            .anyMatch(n -> n.getDatasetName().equals("orders_enriched")),
            "orders_enriched debe estar en downstream");
        assertTrue(downstream.stream()
            .anyMatch(n -> n.getDatasetName().equals("orders_valid")),
            "orders_valid debe estar en downstream");
        assertTrue(downstream.stream()
            .anyMatch(n -> n.getDatasetName().equals("warehouse")),
            "warehouse debe estar en downstream");
    }

    @Test
    @DisplayName("consulta upstream retorna el origen de un campo")
    void upstream_retornaOrigenDelCampo() {
        DataBatch left = buildBatch("orders_raw",
            field("customer_id", FieldType.LONG));
        DataBatch right = buildBatch("customers_raw",
            field("customer_id", FieldType.LONG));
        DataBatch output = buildBatch("orders_enriched",
            field("customer_id", FieldType.LONG));

        recorder.recordSource(left,  "extract-orders");
        recorder.recordSource(right, "extract-customers");
        recorder.recordJoin(left, right, output, "join");

        List<LineageNode> upstream =
            graph.upstream("orders_enriched", "customer_id");

        assertEquals(2, upstream.size(),
            "customer_id en enriched debe venir de 2 fuentes (left y right)");
    }

    @Test
    @DisplayName("campo inexistente retorna lista vacia en upstream y downstream")
    void campoInexistente_listaVacia() {
        assertTrue(graph.upstream("no-existe", "campo").isEmpty());
        assertTrue(graph.downstream("no-existe", "campo").isEmpty());
    }

    @Test
    @DisplayName("getSources retorna nodos sin predecesores")
    void getSources_nodosSinPredecesores() {
        DataBatch source = buildBatch("orders_raw",
            field("id", FieldType.LONG));
        DataBatch output = buildBatch("orders_valid",
            field("id", FieldType.LONG));

        recorder.recordSource(source, "extract");
        recorder.recordFilter(source, output, "filter");

        List<LineageNode> sources = graph.getSources();

        assertEquals(1, sources.size());
        assertEquals("orders_raw", sources.get(0).getDatasetName());
    }

    //Helpers
    private DataBatch buildBatch(String dataset, FieldDefinition... fields) {
        DataSchema schema = new DataSchema(dataset, List.of(fields));
        return DataBatch.builder()
                .datasetName(dataset)
                .schema(schema)
                .build();
    }

    private FieldDefinition field(String name, FieldType type) {
        return new FieldDefinition(name, type, true);
    }
}