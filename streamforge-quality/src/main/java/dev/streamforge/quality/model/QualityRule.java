package dev.streamforge.quality.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Definicion de una regla de calidad para un campo especifico.
 */
public class QualityRule {

    private final String fieldName;
    private final RuleType type;
    private final Map<String, Object> params;

    public QualityRule(String fieldName, RuleType type, Map<String, Object> params) {
        this.fieldName = fieldName;
        this.type      = type;
        this.params    = new HashMap<>(params);
    }

    public QualityRule(String fieldName, RuleType type) {
        this(fieldName, type, Map.of());
    }

    public String getFieldName()           { return fieldName;          }
    public RuleType getType()              { return type;               }
    public Map<String, Object> getParams() { return Map.copyOf(params); }

    public String getParam(String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    public double getDoubleParam(String key, double defaultValue) {
        Object val = params.get(key);
        if (val == null) return defaultValue;
        return Double.parseDouble(val.toString());
    }

    public int getIntParam(String key, int defaultValue) {
        Object val = params.get(key);
        if (val == null) return defaultValue;
        return Integer.parseInt(val.toString());
    }

    @Override
    public String toString() {
        return "QualityRule{field='" + fieldName + "', type=" + type
                + ", params=" + params + "}";
    }
}