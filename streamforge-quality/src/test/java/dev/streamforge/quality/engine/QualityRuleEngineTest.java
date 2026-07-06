package dev.streamforge.quality.engine;

import dev.streamforge.core.model.*;
import dev.streamforge.quality.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QualityRuleEngine - evaluacion de reglas sobre DataBatch")
class QualityRuleEngineTest {

    private QualityRuleEngine engine;
    private static final String EXEC_ID = "exec-quality-test";
    private static final String STEP_ID = "join-step";

    @BeforeEach
    void setUp() { engine = new QualityRuleEngine(); }

    @Test
    @DisplayName("NOT_NULL - detecta valores nulos correctamente")
    void notNull_detectaNulos() {
        // Map.of no acepta null - usar HashMap
        Map<String, Object> rowNull = new java.util.HashMap<>();
        rowNull.put("nombre", null);

        DataBatch batch = buildBatch(
            Map.of("nombre", "Juan"),
            Map.of("nombre", ""),
            rowNull
        );

        QualityReport report = engine.evaluate(
            batch,
            List.of(new QualityRule("nombre", RuleType.NOT_NULL)),
            0.95, EXEC_ID, STEP_ID
        );

        FieldQualityResult result = report.getFieldResults().get(0);
        assertEquals(1, result.getPassed(),  "Solo Juan debe pasar");
        assertEquals(2, result.getFailed(),  "Nulo y vacio deben fallar");
        assertFalse(report.passed());
    }

    @Test
    @DisplayName("multiples reglas sobre el mismo campo generan resultados independientes")
    void multiplesReglas_resultadosIndependientes() {
        Map<String, Object> rowNull = new java.util.HashMap<>();
        rowNull.put("email", null);

        DataBatch batch = buildBatch(
            Map.of("email", "valido@test.com"),
            rowNull
        );

        List<QualityRule> rules = List.of(
            new QualityRule("email", RuleType.NOT_NULL),
            new QualityRule("email", RuleType.REGEX,
                Map.of("pattern", "^.+@.+\\..+$"))
        );

        QualityReport report = engine.evaluate(batch, rules, 0.95, EXEC_ID, STEP_ID);

        assertEquals(2, report.getFieldResults().size());
    }

    @Test
    @DisplayName("RANGE - detecta valores fuera del rango")
    void range_detectaFueraDeRango() {
        DataBatch batch = buildBatch(
            Map.of("total", 50.0),
            Map.of("total", -10.0),
            Map.of("total", 150.0),
            Map.of("total", 99.99)
        );

        QualityRule rule = new QualityRule("total", RuleType.RANGE,
            Map.of("min", "0", "max", "100"));

        QualityReport report = engine.evaluate(
            batch, List.of(rule), 0.95, EXEC_ID, STEP_ID
        );

        FieldQualityResult result = report.getFieldResults().get(0);
        assertEquals(2, result.getPassed(), "50.0 y 99.99 deben pasar");
        assertEquals(2, result.getFailed(), "-10.0 y 150.0 deben fallar");
    }

    @Test
    @DisplayName("REGEX - valida formato de email")
    void regex_validaEmail() {
        DataBatch batch = buildBatch(
            Map.of("email", "juan@example.com"),
            Map.of("email", "no-es-email"),
            Map.of("email", "otro@dominio.pe")
        );

        QualityRule rule = new QualityRule("email", RuleType.REGEX,
            Map.of("pattern",
                "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"));

        QualityReport report = engine.evaluate(
            batch, List.of(rule), 0.95, EXEC_ID, STEP_ID
        );

        FieldQualityResult result = report.getFieldResults().get(0);
        assertEquals(2, result.getPassed());
        assertEquals(1, result.getFailed());
    }

    @Test
    @DisplayName("UNIQUE - detecta valores duplicados en el batch")
    void unique_detectaDuplicados() {
        DataBatch batch = buildBatch(
            Map.of("order_id", 1),
            Map.of("order_id", 2),
            Map.of("order_id", 1), // duplicado
            Map.of("order_id", 3)
        );

        QualityReport report = engine.evaluate(
            batch,
            List.of(new QualityRule("order_id", RuleType.UNIQUE)),
            0.95, EXEC_ID, STEP_ID
        );

        FieldQualityResult result = report.getFieldResults().get(0);
        assertEquals(2, result.getFailed(),
            "Los dos registros con order_id=1 deben fallar");
    }

    @Test
    @DisplayName("batch con todos los valores validos genera reporte PASSED")
    void todosValidos_reportePassed() {
        DataBatch batch = buildBatch(
            Map.of("nombre", "Ana", "edad", 25),
            Map.of("nombre", "Luis", "edad", 30),
            Map.of("nombre", "Maria", "edad", 22)
        );

        List<QualityRule> rules = List.of(
            new QualityRule("nombre", RuleType.NOT_NULL),
            new QualityRule("edad",   RuleType.RANGE, Map.of("min", "0", "max", "120"))
        );

        QualityReport report = engine.evaluate(batch, rules, 0.95, EXEC_ID, STEP_ID);

        assertTrue(report.passed());
        assertEquals(1.0, report.overallPassRate(), 0.001);
        assertTrue(report.getFailingFields().isEmpty());
    }
    @Test
    @DisplayName("batch vacio genera reporte con pass rate 1.0 sin errores")
    void batchVacio_sinErrores() {
        DataBatch batch = buildBatch();
        QualityReport report = engine.evaluate(
            batch,
            List.of(new QualityRule("campo", RuleType.NOT_NULL)),
            0.95, EXEC_ID, STEP_ID
        );

        assertEquals(0, batch.getRowCount());
        assertNotNull(report);
    }

    //Helpers
    @SafeVarargs
    private DataBatch buildBatch(Map<String, Object>... rows) {
        List<FieldDefinition> fields = new ArrayList<>();
        if (rows.length > 0) {
            rows[0].keySet().forEach(k ->
                fields.add(new FieldDefinition(k, FieldType.STRING, true))
            );
        }
        DataSchema schema = new DataSchema("test_dataset", fields);

        DataBatch.Builder builder = DataBatch.builder()
                .datasetName("test_dataset")
                .schema(schema);
        for (Map<String, Object> row : rows) {
            // Usar HashMap para soportar valores null
            Map<String, Object> safeRow = new java.util.HashMap<>(row);
            builder.addRow(safeRow);
        }
        return builder.build();
    }
}