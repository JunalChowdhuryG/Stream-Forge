package dev.streamforge.quality.rules;

import dev.streamforge.quality.model.FieldQualityResult;
import dev.streamforge.quality.model.QualityRule;
import dev.streamforge.quality.model.RuleType;
import dev.streamforge.quality.model.RuleViolation;

import java.util.List;
import java.util.Map;

/**
 * Interfaz para evaluadores de reglas de calidad.
 * Cada implementacion evalua un tipo especifico de regla
 * sobre todos los registros de un campo en un DataBatch.
 */
public interface RuleEvaluator {

    RuleType getSupportedType();

    /**
     * Evalua la regla sobre todos los valores del campo.
     *
     * @param values lista de valores del campo (puede contener nulls)
     * @param rule   regla a evaluar con sus parametros
     * @return resultado con conteo de passed y failed
     */
    FieldQualityResult evaluate(List<Object> values, QualityRule rule);

    /**
     * Evalua un valor individual. Retorna null si pasa, o la violacion si falla.
     */
    RuleViolation evaluateValue(Object value, QualityRule rule);

    /**
     * Implementacion default que evalua todos los valores y agrega el resultado.
     */
    default FieldQualityResult evaluateAll(List<Object> values, QualityRule rule) {
        long passed = 0;
        long failed = 0;

        for (Object value : values) {
            RuleViolation violation = evaluateValue(value, rule);
            if (violation == null) {
                passed++;
            } else {
                failed++;
            }
        }

        return new FieldQualityResult(
            rule.getFieldName(), rule.getType(),
            values.size(), passed, failed
        );
    }
}