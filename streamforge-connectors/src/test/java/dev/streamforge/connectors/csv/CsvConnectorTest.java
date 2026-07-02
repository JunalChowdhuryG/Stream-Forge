package dev.streamforge.connectors.csv;

import dev.streamforge.connectors.api.ConnectorConfig;
import dev.streamforge.connectors.api.WriteResult;
import dev.streamforge.core.model.*;
import dev.streamforge.core.model.execution.ExecutionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CsvConnector - lectura y escritura de archivos CSV")
class CsvConnectorTest {

    @TempDir
    Path tempDir;

    private CsvConnector connector;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        connector = new CsvConnector();
        var pipeline = new dev.streamforge.core.model.PipelineDefinition();
        pipeline.setId("test");
        ctx = new ExecutionContext("exec-csv-test", pipeline, Map.of());
    }

    @Test
    @DisplayName("leer CSV con cabecera detecta schema y filas correctamente")
    void leer_conCabecera() throws IOException {
        Path csv = tempDir.resolve("orders.csv");
        Files.writeString(csv, """
            order_id,customer_id,total
            1,101,150.50
            2,102,200.00
            3,103,75.25
            """);

        ConnectorConfig config = new ConnectorConfig("csv",
            Map.of("path", csv.toString(), "dataset", "orders_raw"));

        DataBatch batch = connector.read(config, ctx);

        assertEquals("orders_raw", batch.getDatasetName());
        assertEquals(3, batch.getRowCount());
        assertEquals(3, batch.getSchema().getFieldCount());
        assertEquals("1", batch.getRows().get(0).get("order_id"));
        assertEquals("150.50", batch.getRows().get(0).get("total"));
    }

    @Test
    @DisplayName("escribir y releer CSV produce el mismo contenido")
    void escribirYReleer_mismoContenido() throws IOException {
        Path output = tempDir.resolve("output.csv");

        DataSchema schema = new DataSchema("test",
            List.of(
                new FieldDefinition("id",    FieldType.INTEGER, false),
                new FieldDefinition("nombre",FieldType.STRING,  true)
            ));

        DataBatch batch = DataBatch.builder()
                .datasetName("test")
                .schema(schema)
                .addRow(Map.of("id", 1, "nombre", "Ana"))
                .addRow(Map.of("id", 2, "nombre", "Luis"))
                .build();

        ConnectorConfig writeConfig = new ConnectorConfig("csv",
            Map.of("path", output.toString()));
        WriteResult result = connector.write(batch, writeConfig, ctx);

        assertEquals(2, result.rowsWritten());
        assertEquals(0, result.rowsRejected());

        // Releer y verificar
        ConnectorConfig readConfig = new ConnectorConfig("csv",
            Map.of("path", output.toString(), "dataset", "test"));
        DataBatch reread = connector.read(readConfig, ctx);

        assertEquals(2, reread.getRowCount());
        assertEquals("1", reread.getRows().get(0).get("id"));
        assertEquals("Ana", reread.getRows().get(0).get("nombre"));
    }

    @Test
    @DisplayName("leer CSV sin cabecera genera columnas col_0, col_1...")
    void leer_sinCabecera() throws IOException {
        Path csv = tempDir.resolve("data.csv");
        Files.writeString(csv, "1,Ana,Lima\n2,Luis,Cusco\n");

        ConnectorConfig config = new ConnectorConfig("csv",
            Map.of("path", csv.toString(),
                   "hasHeader", "false",
                   "dataset", "personas"));

        DataBatch batch = connector.read(config, ctx);

        assertEquals(2, batch.getRowCount());
        assertTrue(batch.getSchema().hasField("col_0"));
        assertTrue(batch.getSchema().hasField("col_1"));
        assertTrue(batch.getSchema().hasField("col_2"));
    }

    @Test
    @DisplayName("healthCheck retorna healthy cuando path esta configurado")
    void healthCheck_pathConfigurado() {
        ConnectorConfig config = new ConnectorConfig("csv",
            Map.of("path", "/alguna/ruta.csv"));
        assertTrue(connector.healthCheck(config).healthy());
    }

    @Test
    @DisplayName("healthCheck retorna unhealthy cuando path no esta configurado")
    void healthCheck_sinPath() {
        ConnectorConfig config = new ConnectorConfig("csv", Map.of());
        assertFalse(connector.healthCheck(config).healthy());
    }
}