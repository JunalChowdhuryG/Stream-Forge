package dev.streamforge.quality.rules;

import dev.streamforge.quality.model.*;

import java.util.List;

/**
 * Evalua que el valor numerico este en el rango [min, max].
 * Los nulls se ignoran - combinar con NOT_NULL para validar ambos.
 */
public class RangeEvaluator implements RuleEvaluator {

    @Override
    public RuleType getSupportedType() { return RuleType.RANGE; }

    @Override
    public FieldQualityResult evaluate(List<Object> values, QualityRule rule) {
        return evaluateAll(values, rule);
    }

    @Override
    public RuleViolation evaluateValue(Object value, QualityRule rule) {
        if (value == null) return null; // null no viola el rango

        double min = rule.getDoubleParam("min", Double.NEGATIVE_INFINITY);
        double max = rule.getDoubleParam("max", Double.POSITIVE_INFINITY);

        double numericValue;
        try {
            numericValue = Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return new RuleViolation(rule.getFieldName(), rule.getType(),
                value, "El valor no es numerico: " + value);
        }

        if (numericValue < min || numericValue > max) {
            return new RuleViolation(rule.getFieldName(), rule.getType(),
                value, String.format("Valor %.2f fuera del rango [%.2f, %.2f]",
                    numericValue, min, max));
        }
        return null;
    }
}