package dev.streamforge.quality.model;

/**
 * Una violacion de regla de calidad detectada en una fila especifica.
 */
public record RuleViolation(
    String fieldName,
    RuleType ruleType,
    Object actualValue,
    String reason
) {
    @Override
    public String toString() {
        return "RuleViolation{field='" + fieldName + "', rule=" + ruleType
                + ", value=" + actualValue + ", reason='" + reason + "'}";
    }
}