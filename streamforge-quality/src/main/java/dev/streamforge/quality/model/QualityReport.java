package dev.streamforge.quality.model;

import java.time.Instant;
import java.util.List;

/**
 * Reporte completo de calidad para un dataset en una ejecucion de pipeline.
 */
public class QualityReport {

    private final String executionId;
    private final String stepId;
    private final String datasetName;
    private final long totalRows;
    private final double threshold;
    private final List<FieldQualityResult> fieldResults;
    private final Instant evaluatedAt;

    public QualityReport(String executionId, String stepId, String datasetName,
                          long totalRows, double threshold,
                          List<FieldQualityResult> fieldResults) {
        this.executionId   = executionId;
        this.stepId        = stepId;
        this.datasetName   = datasetName;
        this.totalRows     = totalRows;
        this.threshold     = threshold;
        this.fieldResults  = List.copyOf(fieldResults);
        this.evaluatedAt   = Instant.now();
    }

    /**
     * Retorna true si TODOS los campos cumplen el threshold configurado.
     */
    public boolean passed() {
        return fieldResults.stream().allMatch(r -> r.meetsThreshold(threshold));
    }

    /**
     * Tasa de aprobacion global - promedio de pass rates de todos los campos.
     */
    public double overallPassRate() {
        if (fieldResults.isEmpty()) return 1.0;
        return fieldResults.stream()
                .mapToDouble(FieldQualityResult::getPassRate)
                .average()
                .orElse(1.0);
    }

    /**
     * Campos que no cumplen el threshold.
     */
    public List<FieldQualityResult> getFailingFields() {
        return fieldResults.stream()
                .filter(r -> !r.meetsThreshold(threshold))
                .toList();
    }

    public String getExecutionId()              { return executionId;  }
    public String getStepId()                   { return stepId;       }
    public String getDatasetName()              { return datasetName;  }
    public long getTotalRows()                  { return totalRows;    }
    public double getThreshold()                { return threshold;    }
    public List<FieldQualityResult> getFieldResults() { return fieldResults; }
    public Instant getEvaluatedAt()             { return evaluatedAt;  }
}