package dev.streamforge.core.lineage;

import java.time.Instant;

/**
 * Arista dirigida en el grafo de linaje
 * Conecta un campo fuente con un campo destino e indica que transformacion
 * produjo esa relacion. La direccion es: source -> target
 * Ejemplo:
 *   source: orders_raw:customer_id
 *   target: orders_enriched:customer_id
 *   type:   JOIN
 *   stepId: join-orders-customers
 */
public class LineageEdge {

    private Long id; // asignado por la BD al persistir
    private final String executionId;
    private final LineageNode source;
    private final LineageNode target;
    private final TransformationType transformationType;
    private final String stepId;
    private final Instant createdAt;

    public LineageEdge(String executionId,
                       LineageNode source,
                       LineageNode target,
                       TransformationType transformationType,
                       String stepId) {
        this.executionId        = executionId;
        this.source             = source;
        this.target             = target;
        this.transformationType = transformationType;
        this.stepId             = stepId;
        this.createdAt          = Instant.now();
    }

    public Long getId()                            { return id;                  }
    public void setId(Long id)                     { this.id = id;               }
    public String getExecutionId()                 { return executionId;         }
    public LineageNode getSource()                 { return source;              }
    public LineageNode getTarget()                 { return target;              }
    public TransformationType getTransformationType(){ return transformationType;}
    public String getStepId()                      { return stepId;              }
    public Instant getCreatedAt()                  { return createdAt;           }

    @Override
    public String toString() {
        return "LineageEdge{" + source.nodeKey()
                + " -> " + target.nodeKey()
                + ", type=" + transformationType + "}";
    }
}