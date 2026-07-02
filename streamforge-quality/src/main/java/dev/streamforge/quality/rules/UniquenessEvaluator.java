package dev.streamforge.quality.rules;

import dev.streamforge.quality.model.*;

import java.util.*;

/**
 * Evalua que no haya valores duplicados en el campo dentro del batch.
 * A diferencia de otros evaluadores, necesita ver todos los valores juntos.
 */
public class UniquenessEvaluator implements RuleEvaluator {

    @Override
    public RuleType getSupportedType() { return RuleType.UNIQUE; }

    @Override
    public FieldQualityResult evaluate(List<Object> values, QualityRule rule) {
        Set<Object> seen       = new HashSet<>();
        Set<Object> duplicates = new HashSet<>();

        for (Object value : values) {
            if (value == null) continue;
            if (!seen.add(value)) {
                duplicates.add(value);
            }
        }

        long failed = values.stream()
                .filter(v -> v != null && duplicates.contains(v))
                .count();
        long passed = values.size() - failed;

        return new FieldQualityResult(
            rule.getFieldName(), rule.getType(),
            values.size(), passed, failed
        );
    }

    @Override
    public RuleViolation evaluateValue(Object value, QualityRule rule) {
        // No aplica para este evaluador - requiere contexto del batch completo
        return null;
    }
}