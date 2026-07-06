package dev.streamforge.quality.chaos;

import dev.streamforge.core.model.*;
import dev.streamforge.quality.engine.QualityRuleEngine;
import dev.streamforge.quality.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P3 - Chaos: degradacion silenciosa de calidad detectada")
class QualityDegradationChaosTest {

    private final QualityRuleEngine engine = new QualityRuleEngine();

    @Test
    @DisplayName("campo con 25% de nulos supera el threshold y genera reporte FALLIDO")
    void degradacion_25PorCientoNulos_reporteFallido() {
        // Simular dataset con 25% de valores nulos en campo critico
        int total    = 100;
        int nullCount = 25;

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < total - nullCount; i++) {
            rows.add(Map.of("customer_id", i + 1, "total", 100.0 + i));
        }
        for (int i = 0; i < nullCount; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("customer_id", null);
            row.put("total", 50.0);
            rows.add(row);
        }

        DataSchema schema = new DataSchema("orders",
            List.of(
                new FieldDefinition("customer_id", FieldType.LONG,   false),
                new FieldDefinition("total",       FieldType.DOUBLE, true)
            ));

        DataBatch batch = DataBatch.builder()
                .datasetName("orders")
                .schema(schema)
                .rows(rows)
                .build();

        QualityReport report = engine.evaluate(
            batch,
            List.of(new QualityRule("customer_id", RuleType.NOT_NULL)),
            0.95,
            "exec-degradation", "join-step"
        );

        assertFalse(report.passed(),
            "Reporte debe fallar con 25% de nulos (threshold: 95%)");

        double passRate = report.getFieldResults().get(0).getPassRate();
        assertEquals(0.75, passRate, 0.001,
            "Pass rate debe ser 75% con 25% de nulos");

        assertFalse(report.getFailingFields().isEmpty(),
            "customer_id debe estar en la lista de campos fallidos");
    }

    @Test
    @DisplayName("campo con 2% de nulos pasa el threshold correctamente")
    void calidadNormal_2PorCientoNulos_reporteOK() {
        int total     = 100;
        int nullCount = 2;

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < total - nullCount; i++) {
            rows.add(Map.of("customer_id", i + 1));
        }
        for (int i = 0; i < nullCount; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("customer_id", null);
            rows.add(row);
        }

        DataSchema schema = new DataSchema("orders",
            List.of(new FieldDefinition("customer_id", FieldType.LONG, false)));

        DataBatch batch = DataBatch.builder()
                .datasetName("orders").schema(schema).rows(rows).build();

        QualityReport report = engine.evaluate(
            batch,
            List.of(new QualityRule("customer_id", RuleType.NOT_NULL)),
            0.95, "exec-ok", "join-step"
        );

        assertTrue(report.passed(),
            "Reporte debe pasar con solo 2% de nulos (threshold: 95%)");
    }

    @Test
    @DisplayName("degradacion progresiva - deteccion en el batch critico")
    void degradacionProgresiva_deteccionEnBatchCritico() {
        double[] nullRates    = { 0.01, 0.05, 0.10, 0.20, 0.25 };
        boolean[] shouldPass  = { true,  true, true, false, false };
        double threshold      = 0.85;

        for (int i = 0; i < nullRates.length; i++) {
            int total     = 100;
            int nullCount = (int)(total * nullRates[i]);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (int j = 0; j < total - nullCount; j++) {
                rows.add(Map.of("campo", "valor-" + j));
            }
            for (int j = 0; j < nullCount; j++) {
                Map<String, Object> row = new HashMap<>();
                row.put("campo", null);
                rows.add(row);
            }

            DataSchema schema = new DataSchema("ds",
                List.of(new FieldDefinition("campo", FieldType.STRING, true)));
            DataBatch batch = DataBatch.builder()
                    .datasetName("ds").schema(schema).rows(rows).build();

            QualityReport report = engine.evaluate(
                batch,
                List.of(new QualityRule("campo", RuleType.NOT_NULL)),
                threshold, "exec-prog-" + i, "step"
            );

            assertEquals(shouldPass[i], report.passed(),
                String.format("Con %.0f%% de nulos y threshold %.0f%%, passed debe ser %b",
                    nullRates[i] * 100, threshold * 100, shouldPass[i]));
        }
    }
}