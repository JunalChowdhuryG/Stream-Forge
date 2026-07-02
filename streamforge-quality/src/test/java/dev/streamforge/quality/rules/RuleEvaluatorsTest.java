package dev.streamforge.quality.rules;

import dev.streamforge.quality.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleEvaluators - tests unitarios por evaluador")
class RuleEvaluatorsTest {

    @Test
    @DisplayName("NotNullEvaluator - null retorna violacion")
    void notNull_null() {
        var evaluator = new NotNullEvaluator();
        var rule      = new QualityRule("campo", RuleType.NOT_NULL);

        assertNotNull(evaluator.evaluateValue(null,  rule));
        assertNotNull(evaluator.evaluateValue("",    rule));
        assertNotNull(evaluator.evaluateValue("   ", rule));
        assertNull(evaluator.evaluateValue("valor",  rule));
        assertNull(evaluator.evaluateValue(123,      rule));
    }

    @Test
    @DisplayName("RangeEvaluator - limites inclusivos")
    void range_limitesInclusivos() {
        var evaluator = new RangeEvaluator();
        var rule = new QualityRule("campo", RuleType.RANGE,
                Map.of("min", "0", "max", "100"));

        assertNull(evaluator.evaluateValue(0,     rule)); // limite inferior
        assertNull(evaluator.evaluateValue(100,   rule)); // limite superior
        assertNull(evaluator.evaluateValue(50.5,  rule)); // en rango
        assertNotNull(evaluator.evaluateValue(-1,  rule)); // bajo minimo
        assertNotNull(evaluator.evaluateValue(101, rule)); // sobre maximo
        assertNull(evaluator.evaluateValue(null,   rule)); // null se ignora
    }

    @Test
    @DisplayName("RegexEvaluator - patron de email")
    void regex_email() {
        var evaluator = new RegexEvaluator();
        var rule = new QualityRule("email", RuleType.REGEX,
                Map.of("pattern",
                    "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"));

        assertNull(evaluator.evaluateValue("test@example.com", rule));
        assertNull(evaluator.evaluateValue("user.name+tag@domain.co", rule));
        assertNotNull(evaluator.evaluateValue("no-es-email",   rule));
        assertNotNull(evaluator.evaluateValue("@sin-local",    rule));
        assertNull(evaluator.evaluateValue(null,               rule));
    }

    @Test
    @DisplayName("FreshnessEvaluator - timestamp reciente pasa, antiguo falla")
    void freshness_recienteVsAntiguo() {
        var evaluator = new FreshnessEvaluator();
        var rule = new QualityRule("created_at", RuleType.FRESHNESS,
                Map.of("maxAgeHours", "24"));

        Instant recent = Instant.now().minus(1,  ChronoUnit.HOURS);
        Instant old    = Instant.now().minus(25, ChronoUnit.HOURS);

        assertNull(evaluator.evaluateValue(recent, rule));
        assertNotNull(evaluator.evaluateValue(old,  rule));
        assertNull(evaluator.evaluateValue(null,    rule));
    }

    @Test
    @DisplayName("RegexEvaluator - patron invalido genera violacion descriptiva")
    void regex_patronInvalido() {
        var evaluator = new RegexEvaluator();
        var rule = new QualityRule("campo", RuleType.REGEX,
                Map.of("pattern", "[patron-invalido"));

        RuleViolation violation = evaluator.evaluateValue("valor", rule);
        assertNotNull(violation);
        assertTrue(violation.reason().contains("invalido"));
    }
}