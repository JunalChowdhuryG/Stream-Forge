package dev.streamforge.connectors.kafka;

import dev.streamforge.connectors.api.ConnectorConfig;
import dev.streamforge.connectors.api.WriteResult;
import dev.streamforge.core.model.*;
import dev.streamforge.core.model.execution.ExecutionContext;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@DisplayName("KafkaConnector - integration test con Kafka real")
class KafkaConnectorIT {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    private KafkaConnector connector;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        connector = new KafkaConnector();
        var pipeline = new dev.streamforge.core.model.PipelineDefinition();
        pipeline.setId("test");
        ctx = new ExecutionContext("exec-kafka-test", pipeline, Map.of());
    }

    @Test
    @DisplayName("escribir mensajes a Kafka y releerlos correctamente")
    void escribirYReleer() throws InterruptedException {
        String topic   = "test-orders-" + System.currentTimeMillis();
        String brokers = kafka.getBootstrapServers();

        DataSchema schema = new DataSchema("orders",
            List.of(
                new FieldDefinition("order_id",   FieldType.INTEGER, false),
                new FieldDefinition("customer_id",FieldType.INTEGER, false),
                new FieldDefinition("total",      FieldType.DOUBLE,  true)
            ));

        DataBatch batch = DataBatch.builder()
                .datasetName("orders")
                .schema(schema)
                .addRow(Map.of("order_id", 1, "customer_id", 101, "total", 150.50))
                .addRow(Map.of("order_id", 2, "customer_id", 102, "total", 200.00))
                .addRow(Map.of("order_id", 3, "customer_id", 103, "total", 75.25))
                .build();

        // Escribir
        ConnectorConfig writeConfig = new ConnectorConfig("kafka",
            Map.of("topic", topic, "bootstrapServers", brokers));
        WriteResult result = connector.write(batch, writeConfig, ctx);

        assertEquals(3, result.rowsWritten());
        assertEquals(0, result.rowsRejected());

        // Esperar propagacion
        Thread.sleep(1000);

        // Leer
        ConnectorConfig readConfig = new ConnectorConfig("kafka",
            Map.of("topic",            topic,
                   "bootstrapServers", brokers,
                   "groupId",          "test-consumer-" + System.currentTimeMillis(),
                   "maxRecords",       "10",
                   "pollTimeoutMs",    "5000",
                   "dataset",          "orders_from_kafka"));
        DataBatch read = connector.read(readConfig, ctx);

        assertEquals(3, read.getRowCount(),
            "Deben leerse los 3 mensajes escritos");
    }

    @Test
    @DisplayName("healthCheck retorna healthy con broker disponible")
    void healthCheck_brokerDisponible() {
        ConnectorConfig config = new ConnectorConfig("kafka",
            Map.of("bootstrapServers", kafka.getBootstrapServers()));
        assertTrue(connector.healthCheck(config).healthy());
    }
}