package dev.streamforge.quality.rules;

import dev.streamforge.quality.model.*;

import java.util.List;

/**
 * Evalua que el campo no tenga valores nulos o strings vacios.
 */
public class NotNullEvaluator implements RuleEvaluator {

    @Override
    public RuleType getSupportedType() { return RuleType.NOT_NULL; }

    @Override
    public FieldQualityResult evaluate(List<Object> values, QualityRule rule) {
        return evaluateAll(values, rule);
    }

    @Override
    public RuleViolation evaluateValue(Object value, QualityRule rule) {
        if (value == null) {
            return new RuleViolation(rule.getFieldName(), rule.getType(),
                null, "El campo es nulo");
        }
        if (value instanceof String s && s.isBlank()) {
            return new RuleViolation(rule.getFieldName(), rule.getType(),
                value, "El campo es una cadena vacia");
        }
        return null; // pasa
    }
}