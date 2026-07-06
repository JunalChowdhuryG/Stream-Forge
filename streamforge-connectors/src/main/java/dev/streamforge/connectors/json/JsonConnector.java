package dev.streamforge.connectors.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.streamforge.connectors.api.*;
import dev.streamforge.core.model.*;
import dev.streamforge.core.model.execution.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Conector para archivos JSON como fuente y destino.
 * Soporta JSON array y JSON Lines (un objeto JSON por linea).
 *
 * Configuracion SOURCE:
 *   path:    ruta del archivo JSON
 *   format:  ARRAY | LINES (default: ARRAY)
 *   dataset: nombre del dataset de salida
 *
 * Configuracion SINK:
 *   path:    ruta del archivo de salida
 *   format:  ARRAY | LINES (default: ARRAY)
 *   pretty:  true/false (default: false)
 */
public class JsonConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(JsonConnector.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getType() { return "json"; }

    @Override
    @SuppressWarnings("unchecked")
    public DataBatch read(ConnectorConfig config, ExecutionContext ctx) {
        String path    = config.getString("path");
        String format  = config.getString("format", "ARRAY");
        String dataset = config.getString("dataset", extractDatasetName(path));

        log.info("JSON read - path={}, format={}, dataset={}", path, format, dataset);

        try {
            List<Map<String, Object>> rows = new ArrayList<>();

            if ("LINES".equalsIgnoreCase(format)) {
                // JSON Lines: un objeto JSON por linea
                try (BufferedReader reader = Files.newBufferedReader(Path.of(path))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            rows.add(mapper.readValue(line, Map.class));
                        }
                    }
                }
            } else {
                // JSON Array: [ {...}, {...} ]
                List<Map<String, Object>> parsed =
                    mapper.readValue(new File(path),
                        mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                rows.addAll(parsed);
            }

            DataSchema schema = inferSchema(dataset, rows);
            DataBatch batch = DataBatch.builder()
                    .datasetName(dataset)
                    .schema(schema)
                    .rows(rows)
                    .totalRowsFromSource(rows.size())
                    .build();

            log.info("JSON leido - rows={}", batch.getRowCount());
            return batch;

        } catch (IOException e) {
            throw new RuntimeException("Error leyendo JSON: " + path, e);
        }
    }

    @Override
    public WriteResult write(DataBatch batch, ConnectorConfig config,
                              ExecutionContext ctx) {
        String path   = config.getString("path");
        String format = config.getString("format", "ARRAY");
        boolean pretty = config.getBoolean("pretty", false);

        log.info("JSON write - path={}, format={}, rows={}", path, format, batch.getRowCount());

        long startMs = System.currentTimeMillis();

        try {
            ObjectMapper writeMapper = pretty
                ? mapper.copy().enable(SerializationFeature.INDENT_OUTPUT)
                : mapper;

            if ("LINES".equalsIgnoreCase(format)) {
                try (BufferedWriter writer = Files.newBufferedWriter(Path.of(path))) {
                    for (Map<String, Object> row : batch.getRows()) {
                        writer.write(writeMapper.writeValueAsString(row));
                        writer.newLine();
                    }
                }
            } else {
                writeMapper.writeValue(new File(path), batch.getRows());
            }

            long duration = System.currentTimeMillis() - startMs;
            log.info("JSON escrito - rows={}, duration={}ms", batch.getRowCount(), duration);
            return WriteResult.of(batch.getRowCount(), 0, path, duration);

        } catch (IOException e) {
            throw new RuntimeException("Error escribiendo JSON: " + path, e);
        }
    }

    @Override
    public ConnectionHealth healthCheck(ConnectorConfig config) {
        String path = config.getString("path");
        if (path == null || path.isBlank()) {
            return ConnectionHealth.failed("json", "Parametro 'path' no configurado");
        }
        return ConnectionHealth.ok("json", 0);
    }

    private DataSchema inferSchema(String dataset, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return new DataSchema(dataset, List.of());
        }
        List<FieldDefinition> fields = new ArrayList<>();
        rows.get(0).forEach((key, value) -> {
            FieldType type = inferType(value);
            fields.add(new FieldDefinition(key, type, true));
        });
        return new DataSchema(dataset, fields);
    }

    private FieldType inferType(Object value) {
        if (value == null)              return FieldType.UNKNOWN;
        if (value instanceof Boolean)   return FieldType.BOOLEAN;
        if (value instanceof Integer)   return FieldType.INTEGER;
        if (value instanceof Long)      return FieldType.LONG;
        if (value instanceof Double)    return FieldType.DOUBLE;
        return FieldType.STRING;
    }

    private String extractDatasetName(String path) {
        if (path == null) return "unknown";
        String filename = Path.of(path).getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}