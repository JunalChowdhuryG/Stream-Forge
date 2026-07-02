package dev.streamforge.connectors.csv;

import dev.streamforge.connectors.api.*;
import dev.streamforge.core.model.*;
import dev.streamforge.core.model.execution.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Conector para archivos CSV como fuente y destino.
 *
 * Configuracion SOURCE:
 *   path: ruta del archivo CSV
 *   hasHeader: true/false (default: true)
 *   delimiter: separador (default: ",")
 *   dataset: nombre del dataset de salida
 *
 * Configuracion SINK:
 *   path: ruta del archivo de salida
 *   hasHeader: true/false (default: true)
 *   delimiter: separador (default: ",")
 *   mode: CREATE | APPEND | OVERWRITE (default: OVERWRITE)
 */
public class CsvConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(CsvConnector.class);

    @Override
    public String getType() { return "csv"; }

    @Override
    public DataBatch read(ConnectorConfig config, ExecutionContext ctx) {
        String path       = config.getString("path");
        boolean hasHeader = config.getBoolean("hasHeader", true);
        String delimiter  = config.getString("delimiter", ",");
        String dataset    = config.getString("dataset", extractDatasetName(path));

        log.info("CSV read - path={}, dataset={}", path, dataset);

        try {
            List<String> lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return DataBatch.builder()
                        .datasetName(dataset)
                        .schema(new DataSchema(dataset, List.of()))
                        .build();
            }

            String[] headers;
            int dataStart;

            if (hasHeader) {
                headers   = splitLine(lines.get(0), delimiter);
                dataStart = 1;
            } else {
                int colCount = splitLine(lines.get(0), delimiter).length;
                headers   = new String[colCount];
                for (int i = 0; i < colCount; i++) headers[i] = "col_" + i;
                dataStart = 0;
            }

            // Construir schema inferido como STRING por defecto
            List<FieldDefinition> fields = new ArrayList<>();
            for (String h : headers) {
                fields.add(new FieldDefinition(h.trim(), FieldType.STRING, true));
            }
            DataSchema schema = new DataSchema(dataset, fields);

            // Construir filas
            DataBatch.Builder builder = DataBatch.builder()
                    .datasetName(dataset)
                    .schema(schema)
                    .totalRowsFromSource(lines.size() - dataStart);

            for (int i = dataStart; i < lines.size(); i++) {
                String[] values = splitLine(lines.get(i), delimiter);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int j = 0; j < headers.length; j++) {
                    row.put(headers[j].trim(),
                            j < values.length ? values[j].trim() : null);
                }
                builder.addRow(row);
            }

            DataBatch batch = builder.build();
            log.info("CSV leido - rows={}, cols={}", batch.getRowCount(), headers.length);
            return batch;

        } catch (IOException e) {
            throw new RuntimeException("Error leyendo CSV: " + path, e);
        }
    }

    @Override
    public WriteResult write(DataBatch batch, ConnectorConfig config,
                              ExecutionContext ctx) {
        String path      = config.getString("path");
        boolean header   = config.getBoolean("hasHeader", true);
        String delimiter = config.getString("delimiter", ",");
        String mode      = config.getString("mode", "OVERWRITE");

        log.info("CSV write - path={}, rows={}, mode={}", path, batch.getRowCount(), mode);

        long startMs = System.currentTimeMillis();
        long written = 0;

        try {
            boolean append = "APPEND".equalsIgnoreCase(mode);
            try (BufferedWriter writer = Files.newBufferedWriter(
                    Path.of(path),
                    StandardCharsets.UTF_8,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {

                List<FieldDefinition> fields = batch.getSchema().getFields();

                if (header && !append) {
                    writer.write(String.join(delimiter,
                        fields.stream().map(FieldDefinition::getName).toList()));
                    writer.newLine();
                }

                for (Map<String, Object> row : batch.getRows()) {
                    List<String> values = new ArrayList<>();
                    for (FieldDefinition f : fields) {
                        Object val = row.get(f.getName());
                        values.add(val != null ? val.toString() : "");
                    }
                    writer.write(String.join(delimiter, values));
                    writer.newLine();
                    written++;
                }
            }

            long duration = System.currentTimeMillis() - startMs;
            log.info("CSV escrito - rows={}, duration={}ms", written, duration);
            return WriteResult.of(written, 0, path, duration);

        } catch (IOException e) {
            throw new RuntimeException("Error escribiendo CSV: " + path, e);
        }
    }

    @Override
    public ConnectionHealth healthCheck(ConnectorConfig config) {
        String path = config.getString("path");
        if (path == null || path.isBlank()) {
            return ConnectionHealth.failed("csv", "Parametro 'path' no configurado");
        }
        return ConnectionHealth.ok("csv", 0);
    }

    private String[] splitLine(String line, String delimiter) {
        return line.split(delimiter, -1);
    }

    private String extractDatasetName(String path) {
        if (path == null) return "unknown";
        String filename = Path.of(path).getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}