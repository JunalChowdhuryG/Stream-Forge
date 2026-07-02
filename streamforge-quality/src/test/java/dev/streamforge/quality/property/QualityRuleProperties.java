package dev.streamforge.quality.property;

import dev.streamforge.quality.model.QualityRule;
import dev.streamforge.quality.model.RuleType;
import dev.streamforge.quality.rules.NotNullEvaluator;
import dev.streamforge.quality.rules.RangeEvaluator;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.Assume;
import java.util.List;
import java.util.Map;

/**
 * Property-based tests para los evaluadores de reglas de calidad.
 */
class QualityRuleProperties {

    private final NotNullEvaluator notNull = new NotNullEvaluator();
    private final RangeEvaluator   range   = new RangeEvaluator();

    @Property(tries = 500)
    @Label("Propiedad 1: NOT_NULL - cualquier valor no nulo y no en blanco siempre pasa")
    void prop_notNull_valorNoNuloSiemprePasa(
            @ForAll @net.jqwik.api.constraints.NotEmpty String value) {
        // Excluir strings que son solo espacios - isBlank() los rechaza por diseno
        Assume.that(!value.isBlank());

        QualityRule rule = new QualityRule("campo", RuleType.NOT_NULL);
        assertNull(notNull.evaluateValue(value, rule),
            "Valor no nulo y no en blanco '" + value + "' debe pasar NOT_NULL");
    }

    @Property(tries = 500)
    @Label("Propiedad 2: RANGE - valor en rango siempre pasa, fuera siempre falla")
    void prop_range_valorEnRangoSiemprePasa(
            @ForAll @DoubleRange(min = 0, max = 100) double value) {
        QualityRule rule = new QualityRule("campo", RuleType.RANGE,
                Map.of("min", "0", "max", "100"));

        assertNull(range.evaluateValue(value, rule),
            "Valor " + value + " en [0,100] debe pasar RANGE");
    }

    @Property(tries = 300)
    @Label("Propiedad 3: RANGE - valor fuera del rango siempre falla")
    void prop_range_valorFueraDeRangoSiempreFalla(
            @ForAll @DoubleRange(min = 101, max = 10000) double value) {
        QualityRule rule = new QualityRule("campo", RuleType.RANGE,
                Map.of("min", "0", "max", "100"));

        assertNotNull(range.evaluateValue(value, rule),
            "Valor " + value + " fuera de [0,100] debe fallar RANGE");
    }

    @Property(tries = 200)
    @Label("Propiedad 4: passed + failed siempre suma totalRows")
    void prop_resultados_sumaTotalRows(
            @ForAll @net.jqwik.api.constraints.Size(min=1, max=50)
            List<@net.jqwik.api.constraints.NotEmpty String> values) {
        QualityRule rule = new QualityRule("campo", RuleType.NOT_NULL);
        var result = notNull.evaluate(values.stream().map(v -> (Object) v).toList(), rule);

        assertEquals(values.size(), result.getPassed() + result.getFailed(),
            "passed + failed debe ser igual a totalRows");
    }

    private void assertNull(Object obj, String msg) {
        if (obj != null) throw new AssertionError(msg);
    }

    private void assertNotNull(Object obj, String msg) {
        if (obj == null) throw new AssertionError(msg);
    }

    private void assertEquals(long expected, long actual, String msg) {
        if (expected != actual) throw new AssertionError(
            msg + " (expected " + expected + ", got " + actual + ")");
    }
}