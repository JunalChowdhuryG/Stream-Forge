package dev.streamforge.connectors.postgres;

import dev.streamforge.connectors.api.*;
import dev.streamforge.core.model.*;
import dev.streamforge.core.model.execution.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Conector PostgreSQL como fuente y destino.
 *
 * Configuracion SOURCE:
 *   query:   query SQL con soporte de parametros :param
 *   table:   nombre de tabla (alternativa a query)
 *   columns: lista de columnas a leer (opcional)
 *   dataset: nombre del dataset de salida
 *
 * Configuracion SINK:
 *   table: tabla destino
 *   mode:  INSERT | UPSERT | TRUNCATE_INSERT (default: INSERT)
 *   key:   campo clave para UPSERT
 */
public class PostgresConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(PostgresConnector.class);

    private final DataSource dataSource;

    public PostgresConnector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getType() { return "postgres"; }

    @Override
    public DataBatch read(ConnectorConfig config, ExecutionContext ctx) {
        String dataset = config.getString("dataset", "postgres_result");
        String sql     = buildSelectQuery(config, ctx);

        log.info("Postgres read - query={}", sql);
        long startMs = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            List<FieldDefinition> fields = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                fields.add(new FieldDefinition(
                    meta.getColumnName(i),
                    mapSqlType(meta.getColumnType(i)),
                    meta.isNullable(i) != ResultSetMetaData.columnNoNulls
                ));
            }
            DataSchema schema = new DataSchema(dataset, fields);

            DataBatch.Builder builder = DataBatch.builder()
                    .datasetName(dataset)
                    .schema(schema);

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                builder.addRow(row);
            }

            DataBatch batch = builder.build();
            long duration = System.currentTimeMillis() - startMs;
            log.info("Postgres leido - rows={}, duration={}ms", batch.getRowCount(), duration);
            return batch;

        } catch (SQLException e) {
            throw new RuntimeException("Error en lectura PostgreSQL: " + e.getMessage(), e);
        }
    }

    @Override
    public WriteResult write(DataBatch batch, ConnectorConfig config,
                              ExecutionContext ctx) {
        String table = config.getString("table");
        String mode  = config.getString("mode", "INSERT");
        String key   = config.getString("key");

        log.info("Postgres write - table={}, mode={}, rows={}", table, mode, batch.getRowCount());
        long startMs = System.currentTimeMillis();
        long written = 0;
        long rejected = 0;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            if ("TRUNCATE_INSERT".equalsIgnoreCase(mode)) {
                try (Statement st = conn.createStatement()) {
                    st.execute("TRUNCATE TABLE " + table);
                }
            }

            List<FieldDefinition> fields = batch.getSchema().getFields();
            String sql = buildInsertSql(table, fields, mode, key);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map<String, Object> row : batch.getRows()) {
                    try {
                        for (int i = 0; i < fields.size(); i++) {
                            ps.setObject(i + 1, row.get(fields.get(i).getName()));
                        }
                        // Para UPSERT los campos se repiten para el DO UPDATE SET
                        if ("UPSERT".equalsIgnoreCase(mode) && key != null) {
                            for (int i = 0; i < fields.size(); i++) {
                                ps.setObject(fields.size() + i + 1,
                                    row.get(fields.get(i).getName()));
                            }
                        }
                        ps.addBatch();
                        written++;
                    } catch (SQLException e) {
                        rejected++;
                        log.warn("Fila rechazada - {}", e.getMessage());
                    }
                }
                ps.executeBatch();
                conn.commit();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en escritura PostgreSQL: " + e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startMs;
        log.info("Postgres escrito - written={}, rejected={}, duration={}ms",
                written, rejected, duration);
        return WriteResult.of(written, rejected, table, duration);
    }

    @Override
    public ConnectionHealth healthCheck(ConnectorConfig config) {
        long startMs = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            conn.isValid(3);
            return ConnectionHealth.ok("postgres", System.currentTimeMillis() - startMs);
        } catch (SQLException e) {
            return ConnectionHealth.failed("postgres", e.getMessage());
        }
    }

    // ── Privados ──────────────────────────────────────────────────────────────

    private String buildSelectQuery(ConnectorConfig config, ExecutionContext ctx) {
        String query = config.getString("query");
        if (query != null) {
            // Resolver parametros :param con el ExecutionContext
            String resolved = query;
            for (Map.Entry<String, String> param : ctx.getParameters().entrySet()) {
                resolved = resolved.replace(":" + param.getKey(),
                        "'" + param.getValue() + "'");
            }
            return resolved;
        }

        String table   = config.getString("table");
        String columns = config.getString("columns", "*");
        return "SELECT " + columns + " FROM " + table;
    }

    private String buildInsertSql(String table, List<FieldDefinition> fields,
                                   String mode, String key) {
        String colList = String.join(", ",
                fields.stream().map(FieldDefinition::getName).toList());
        String placeholders = String.join(", ",
                Collections.nCopies(fields.size(), "?"));

        String base = "INSERT INTO " + table + " (" + colList + ") VALUES (" + placeholders + ")";

        if ("UPSERT".equalsIgnoreCase(mode) && key != null) {
            String updateSet = String.join(", ", fields.stream()
                .filter(f -> !f.getName().equals(key))
                .map(f -> f.getName() + " = ?")
                .toList());
            return base + " ON CONFLICT (" + key + ") DO UPDATE SET " + updateSet;
        }

        return base;
    }

    private FieldType mapSqlType(int sqlType) {
        return switch (sqlType) {
            case Types.INTEGER, Types.SMALLINT -> FieldType.INTEGER;
            case Types.BIGINT                  -> FieldType.LONG;
            case Types.DOUBLE, Types.FLOAT     -> FieldType.DOUBLE;
            case Types.DECIMAL, Types.NUMERIC  -> FieldType.DECIMAL;
            case Types.BOOLEAN, Types.BIT      -> FieldType.BOOLEAN;
            case Types.DATE                    -> FieldType.DATE;
            case Types.TIMESTAMP               -> FieldType.TIMESTAMP;
            default                            -> FieldType.STRING;
        };
    }
}