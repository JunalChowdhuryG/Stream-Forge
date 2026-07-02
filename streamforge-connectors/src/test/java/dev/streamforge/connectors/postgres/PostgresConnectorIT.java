package dev.streamforge.connectors.postgres;

import dev.streamforge.connectors.api.ConnectorConfig;
import dev.streamforge.connectors.api.WriteResult;
import dev.streamforge.core.model.*;
import dev.streamforge.core.model.execution.ExecutionContext;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@DisplayName("PostgresConnector - integration test con PostgreSQL real")
class PostgresConnectorIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("connector_test")
            .withUsername("test")
            .withPassword("test");

    private PostgresConnector connector;
    private ExecutionContext ctx;
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;
        connector  = new PostgresConnector(dataSource);

        var pipeline = new dev.streamforge.core.model.PipelineDefinition();
        pipeline.setId("test");
        ctx = new ExecutionContext("exec-pg-test", pipeline, Map.of("date", "2026-01-15"));

        // Crear tablas de prueba
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS test_orders (
                    id       BIGINT PRIMARY KEY,
                    name     VARCHAR(100),
                    amount   DECIMAL(10,2)
                )
                """);
            st.execute("TRUNCATE TABLE test_orders");
            st.execute("""
                INSERT INTO test_orders VALUES
                (1, 'Orden A', 150.50),
                (2, 'Orden B', 200.00),
                (3, 'Orden C', 75.25)
                """);
        }
    }

    @Test
    @DisplayName("leer tabla completa detecta schema y retorna todas las filas")
    void leer_tablaCompleta() {
        ConnectorConfig config = new ConnectorConfig("postgres",
            Map.of("table", "test_orders", "dataset", "orders_raw"));

        DataBatch batch = connector.read(config, ctx);

        assertEquals("orders_raw", batch.getDatasetName());
        assertEquals(3, batch.getRowCount());
        assertTrue(batch.getSchema().hasField("id"));
        assertTrue(batch.getSchema().hasField("name"));
        assertTrue(batch.getSchema().hasField("amount"));
    }

    @Test
    @DisplayName("leer con query SQL filtra registros correctamente")
    void leer_conQuery() {
        ConnectorConfig config = new ConnectorConfig("postgres",
            Map.of("query",   "SELECT * FROM test_orders WHERE amount > 100",
                   "dataset", "orders_expensive"));

        DataBatch batch = connector.read(config, ctx);

        assertEquals(2, batch.getRowCount(),
            "Solo ordenes con amount > 100 deben retornarse");
    }

    @Test
    @DisplayName("escribir con INSERT carga todas las filas")
    void escribir_insert() throws Exception {
        // Crear tabla destino
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS test_orders_dest (
                    id     BIGINT PRIMARY KEY,
                    name   VARCHAR(100),
                    amount DECIMAL(10,2)
                )
                """);
            st.execute("TRUNCATE TABLE test_orders_dest");
        }

        DataSchema schema = new DataSchema("orders",
            List.of(
                new FieldDefinition("id",     FieldType.LONG,    false),
                new FieldDefinition("name",   FieldType.STRING,  true),
                new FieldDefinition("amount", FieldType.DECIMAL, true)
            ));

        DataBatch batch = DataBatch.builder()
                .datasetName("orders")
                .schema(schema)
                .addRow(Map.of("id", 10L, "name", "Nueva Orden", "amount", 99.99))
                .addRow(Map.of("id", 11L, "name", "Otra Orden",  "amount", 49.99))
                .build();

        ConnectorConfig config = new ConnectorConfig("postgres",
            Map.of("table", "test_orders_dest", "mode", "INSERT"));

        WriteResult result = connector.write(batch, config, ctx);

        assertEquals(2, result.rowsWritten());
        assertEquals(0, result.rowsRejected());

        // Verificar que llegaron a la BD
        ConnectorConfig readConfig = new ConnectorConfig("postgres",
            Map.of("table", "test_orders_dest", "dataset", "dest"));
        DataBatch written = connector.read(readConfig, ctx);
        assertEquals(2, written.getRowCount());
    }

    @Test
    @DisplayName("healthCheck retorna healthy con conexion activa")
    void healthCheck_conexionActiva() {
        ConnectorConfig config = new ConnectorConfig("postgres", Map.of());
        assertTrue(connector.healthCheck(config).healthy());
    }
}