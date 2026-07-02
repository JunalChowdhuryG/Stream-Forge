package dev.streamforge.quality.model;

/**
 * Resultado de evaluar una regla sobre todos los registros de un campo.
 */
public class FieldQualityResult {

    private final String fieldName;
    private final RuleType ruleType;
    private final long totalRows;
    private final long passed;
    private final long failed;

    public FieldQualityResult(String fieldName, RuleType ruleType,
                               long totalRows, long passed, long failed) {
        this.fieldName = fieldName;
        this.ruleType  = ruleType;
        this.totalRows = totalRows;
        this.passed    = passed;
        this.failed    = failed;
    }

    public String getFieldName()   { return fieldName;                        }
    public RuleType getRuleType()  { return ruleType;                         }
    public long getTotalRows()     { return totalRows;                        }
    public long getPassed()        { return passed;                           }
    public long getFailed()        { return failed;                           }
    public double getPassRate()    { return totalRows == 0 ? 1.0 : (double) passed / totalRows; }
    public double getFailureRate() { return totalRows == 0 ? 0.0 : (double) failed / totalRows; }

    public boolean meetsThreshold(double threshold) {
        return getPassRate() >= threshold;
    }

    @Override
    public String toString() {
        return String.format("FieldQualityResult{field='%s', rule=%s, passed=%d/%d (%.1f%%)}",
                fieldName, ruleType, passed, totalRows, getPassRate() * 100);
    }
}