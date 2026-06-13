package dev.streamforge.core.model;

import java.time.Instant;
import java.util.*;

/**
 * Lote de datos que fluye entre pasos del pipeline
 * Un DataBatch contiene:
 *   - El schema del dataset (nombres y tipos de columnas)
 *   - Las filas de datos como lista de mapas campo-->valor
 *   - Metadatos de la ejecucion (origen, timestamp, numero de filas)
 *
 * Es la unidad de transferencia entre conectores y transformaciones
 * Inmutable una vez construido - las transformaciones producen nuevos DataBatch
 */
public class DataBatch {

    private final String datasetName;
    private final DataSchema schema;
    private final List<Map<String, Object>> rows;
    private final Instant createdAt;
    private final String sourceStepId;
    private final long totalRowsFromSource;

    private DataBatch(Builder builder) {
        this.datasetName          = Objects.requireNonNull(builder.datasetName);
        this.schema               = Objects.requireNonNull(builder.schema);
        this.rows                 = List.copyOf(builder.rows);
        this.createdAt            = Instant.now();
        this.sourceStepId         = builder.sourceStepId;
        this.totalRowsFromSource  = builder.totalRowsFromSource > 0
                ? builder.totalRowsFromSource : builder.rows.size();
    }

    public String getDatasetName()              { return datasetName;         }
    public DataSchema getSchema()               { return schema;              }
    public List<Map<String, Object>> getRows()  { return rows;                }
    public int getRowCount()                    { return rows.size();         }
    public Instant getCreatedAt()               { return createdAt;           }
    public String getSourceStepId()             { return sourceStepId;        }
    public long getTotalRowsFromSource()        { return totalRowsFromSource; }
    public boolean isEmpty()                    { return rows.isEmpty();      }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String datasetName;
        private DataSchema schema;
        private List<Map<String, Object>> rows = new ArrayList<>();
        private String sourceStepId;
        private long totalRowsFromSource;

        public Builder datasetName(String v)              { datasetName = v;          return this; }
        public Builder schema(DataSchema v)               { schema = v;               return this; }
        public Builder rows(List<Map<String, Object>> v)  { rows = new ArrayList<>(v); return this; }
        public Builder addRow(Map<String, Object> row)    { rows.add(row);            return this; }
        public Builder sourceStepId(String v)             { sourceStepId = v;         return this; }
        public Builder totalRowsFromSource(long v)        { totalRowsFromSource = v;  return this; }
        public DataBatch build()                          { return new DataBatch(this);             }
    }

    @Override
    public String toString() {
        return "DataBatch{dataset='" + datasetName + "', rows=" + rows.size() + "}";
    }
}