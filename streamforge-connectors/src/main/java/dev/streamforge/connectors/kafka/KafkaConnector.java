package dev.streamforge.connectors.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamforge.connectors.api.*;
import dev.streamforge.core.model.*;
import dev.streamforge.core.model.execution.ExecutionContext;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

/**
 * Conector Kafka como fuente y destino.
 *
 * Configuracion SOURCE:
 *   topic:         topic de Kafka a consumir
 *   groupId:       consumer group ID
 *   maxRecords:    maximo de registros a leer (default: 1000)
 *   pollTimeoutMs: timeout de poll en ms (default: 5000)
 *   dataset:       nombre del dataset de salida
 *   bootstrapServers: brokers de Kafka
 *
 * Configuracion SINK:
 *   topic:           topic destino
 *   partitionBy:     campo para calcular la particion (opcional)
 *   bootstrapServers: brokers de Kafka
 */
public class KafkaConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(KafkaConnector.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getType() { return "kafka"; }

    @Override
    @SuppressWarnings("unchecked")
    public DataBatch read(ConnectorConfig config, ExecutionContext ctx) {
        String topic    = config.getString("topic");
        String groupId  = config.getString("groupId",
                "streamforge-" + ctx.getExecutionId());
        int maxRecords  = config.getInt("maxRecords", 1000);
        long pollTimeoutMs = config.getInt("pollTimeoutMs", 5000);
        String dataset  = config.getString("dataset", topic);
        String brokers  = config.getString("bootstrapServers", "localhost:9092");

        log.info("Kafka read - topic={}, maxRecords={}", topic, maxRecords);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  brokers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,           groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,   maxRecords);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());

        List<Map<String, Object>> rows = new ArrayList<>();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));

            ConsumerRecords<String, String> records =
                consumer.poll(Duration.ofMillis(pollTimeoutMs));

            for (ConsumerRecord<String, String> record : records) {
                try {
                    Map<String, Object> row = mapper.readValue(record.value(), Map.class);
                    row.put("_kafka_offset",    record.offset());
                    row.put("_kafka_partition", record.partition());
                    row.put("_kafka_timestamp", record.timestamp());
                    rows.add(row);
                } catch (Exception e) {
                    log.warn("Error parseando mensaje Kafka offset={}: {}",
                            record.offset(), e.getMessage());
                }
            }
        }

        DataSchema schema = inferSchema(dataset, rows);
        DataBatch batch = DataBatch.builder()
                .datasetName(dataset)
                .schema(schema)
                .rows(rows)
                .totalRowsFromSource(rows.size())
                .build();

        log.info("Kafka leido - topic={}, rows={}", topic, batch.getRowCount());
        return batch;
    }

    @Override
    public WriteResult write(DataBatch batch, ConnectorConfig config,
                              ExecutionContext ctx) {
        String topic       = config.getString("topic");
        String partitionBy = config.getString("partitionBy");
        String brokers     = config.getString("bootstrapServers", "localhost:9092");

        log.info("Kafka write - topic={}, rows={}", topic, batch.getRowCount());
        long startMs = System.currentTimeMillis();
        long written = 0;
        long rejected = 0;

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (Map<String, Object> row : batch.getRows()) {
                try {
                    String key   = partitionBy != null
                        ? String.valueOf(row.get(partitionBy)) : null;
                    String value = mapper.writeValueAsString(row);
                    producer.send(new ProducerRecord<>(topic, key, value)).get();
                    written++;
                } catch (Exception e) {
                    rejected++;
                    log.warn("Error enviando mensaje a Kafka: {}", e.getMessage());
                }
            }
            producer.flush();
        }

        long duration = System.currentTimeMillis() - startMs;
        log.info("Kafka escrito - topic={}, written={}, rejected={}, duration={}ms",
                topic, written, rejected, duration);
        return WriteResult.of(written, rejected, topic, duration);
    }

    @Override
    public ConnectionHealth healthCheck(ConnectorConfig config) {
        String brokers = config.getString("bootstrapServers", "localhost:9092");
        long startMs = System.currentTimeMillis();

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "3000");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.partitionsFor("__consumer_offsets");
            return ConnectionHealth.ok("kafka", System.currentTimeMillis() - startMs);
        } catch (Exception e) {
            return ConnectionHealth.failed("kafka", e.getMessage());
        }
    }

    private DataSchema inferSchema(String dataset, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return new DataSchema(dataset, List.of());
        List<FieldDefinition> fields = new ArrayList<>();
        rows.get(0).forEach((k, v) ->
            fields.add(new FieldDefinition(k, FieldType.STRING, true))
        );
        return new DataSchema(dataset, fields);
    }
}