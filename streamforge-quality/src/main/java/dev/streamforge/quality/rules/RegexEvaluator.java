package dev.streamforge.quality.rules;

import dev.streamforge.quality.model.*;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evalua que el valor del campo cumpla la expresion regular configurada.
 * Los nulls se ignoran - combinar con NOT_NULL si se requiere.
 */
public class RegexEvaluator implements RuleEvaluator {

    @Override
    public RuleType getSupportedType() { return RuleType.REGEX; }

    @Override
    public FieldQualityResult evaluate(List<Object> values, QualityRule rule) {
        return evaluateAll(values, rule);
    }

    @Override
    public RuleViolation evaluateValue(Object value, QualityRule rule) {
        if (value == null) return null;

        String pattern = rule.getParam("pattern");
        if (pattern == null || pattern.isBlank()) {
            return new RuleViolation(rule.getFieldName(), rule.getType(),
                value, "Parametro 'pattern' no configurado en la regla REGEX");
        }

        try {
            if (!Pattern.matches(pattern, value.toString())) {
                return new RuleViolation(rule.getFieldName(), rule.getType(),
                    value, "El valor '" + value + "' no cumple el patron: " + pattern);
            }
        } catch (PatternSyntaxException e) {
            return new RuleViolation(rule.getFieldName(), rule.getType(),
                value, "Patron regex invalido: " + pattern);
        }
        return null;
    }
}