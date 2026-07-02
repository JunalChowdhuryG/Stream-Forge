package dev.streamforge.quality.rules;

import dev.streamforge.quality.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Evalua que el timestamp del campo no sea mas antiguo que max_age_hours.
 * Acepta valores como Instant, Long (epoch millis) o String ISO-8601.
 */
public class FreshnessEvaluator implements RuleEvaluator {

    @Override
    public RuleType getSupportedType() { return RuleType.FRESHNESS; }

    @Override
    public FieldQualityResult evaluate(List<Object> values, QualityRule rule) {
        return evaluateAll(values, rule);
    }

    @Override
    public RuleViolation evaluateValue(Object value, QualityRule rule) {
        if (value == null) return null;

        int maxAgeHours = rule.getIntParam("maxAgeHours", 24);

        Instant timestamp;
        try {
            timestamp = parseTimestamp(value);
        } catch (Exception e) {
            return new RuleViolation(rule.getFieldName(), rule.getType(),
                value, "No se pudo parsear el timestamp: " + value);
        }

        Instant cutoff = Instant.now().minus(maxAgeHours, ChronoUnit.HOURS);
        if (timestamp.isBefore(cutoff)) {
            long hoursOld = ChronoUnit.HOURS.between(timestamp, Instant.now());
            return new RuleViolation(rule.getFieldName(), rule.getType(),
                value, String.format("Timestamp tiene %d horas de antiguedad (maximo: %d)",
                    hoursOld, maxAgeHours));
        }
        return null;
    }

    private Instant parseTimestamp(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof Long l)    return Instant.ofEpochMilli(l);
        return Instant.parse(value.toString());
    }
}