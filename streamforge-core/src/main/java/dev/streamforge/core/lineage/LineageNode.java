package dev.streamforge.core.lineage;

import java.time.Instant;
import java.util.Objects;

/**
 * Nodo en el grafo de linaje de datos
 * Representa un campo especifico dentro de un dataset en el contexto
 * de una ejecucion de pipeline. La clave unica es la combinacion de
 * executionId + datasetName + fieldName
 * Ejemplo: executionId="exec-123", dataset="orders_enriched", field="customer_id"
 */
public class LineageNode {

    private Long id; // asignado por la BD al persistir
    private final String executionId;
    private final String datasetName;
    private final String fieldName;
    private final String dataType;
    private final String stepId;
    private final Instant createdAt;

    public LineageNode(String executionId,
                       String datasetName,
                       String fieldName,
                       String dataType,
                       String stepId) {
        this.executionId = Objects.requireNonNull(executionId);
        this.datasetName = Objects.requireNonNull(datasetName);
        this.fieldName   = Objects.requireNonNull(fieldName);
        this.dataType    = dataType;
        this.stepId      = stepId;
        this.createdAt   = Instant.now();
    }

    /**
     * Clave logica del nodo para identificacion en el grafo en memoria.
     */
    public String nodeKey() {
        return datasetName + ":" + fieldName;
    }

    public Long getId()            { return id;          }
    public void setId(Long id)     { this.id = id;       }
    public String getExecutionId() { return executionId; }
    public String getDatasetName() { return datasetName; }
    public String getFieldName()   { return fieldName;   }
    public String getDataType()    { return dataType;    }
    public String getStepId()      { return stepId;      }
    public Instant getCreatedAt()  { return createdAt;   }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LineageNode n)) return false;
        return executionId.equals(n.executionId)
            && datasetName.equals(n.datasetName)
            && fieldName.equals(n.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId, datasetName, fieldName);
    }

    @Override
    public String toString() {
        return "LineageNode{" + datasetName + ":" + fieldName
                + ", type=" + dataType + ", step=" + stepId + "}";
    }
}