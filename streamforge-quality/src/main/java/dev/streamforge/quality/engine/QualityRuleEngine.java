package dev.streamforge.quality.engine;

import dev.streamforge.core.model.DataBatch;
import dev.streamforge.quality.model.*;
import dev.streamforge.quality.rules.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Motor central de evaluacion de reglas de calidad.
 *
 * Orquesta la evaluacion de todas las reglas configuradas sobre un DataBatch
 * y genera un QualityReport con los resultados por campo y regla.
 *
 * Extensible: nuevos evaluadores se registran en el constructor sin
 * modificar el engine.
 */
public class QualityRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(QualityRuleEngine.class);

    private final Map<RuleType, RuleEvaluator> evaluators;

    public QualityRuleEngine() {
        this.evaluators = new EnumMap<>(RuleType.class);
        // Registrar evaluadores disponibles
        register(new NotNullEvaluator());
        register(new RangeEvaluator());
        register(new RegexEvaluator());
        register(new UniquenessEvaluator());
        register(new FreshnessEvaluator());
    }

    private void register(RuleEvaluator evaluator) {
        evaluators.put(evaluator.getSupportedType(), evaluator);
    }

    /**
     * Evalua todas las reglas configuradas sobre el DataBatch dado.
     *
     * @param batch     datos a evaluar
     * @param rules     lista de reglas a aplicar
     * @param threshold porcentaje minimo de registros que deben pasar (0.0 - 1.0)
     * @param executionId ID de la ejecucion para el reporte
     * @param stepId    ID del paso para el reporte
     * @return QualityReport con resultados por campo y regla
     */
    public QualityReport evaluate(DataBatch batch,
                                   List<QualityRule> rules,
                                   double threshold,
                                   String executionId,
                                   String stepId) {
        log.info("Evaluando calidad - dataset={}, rules={}, threshold={}",
                batch.getDatasetName(), rules.size(), threshold);

        List<FieldQualityResult> results = new ArrayList<>();

        for (QualityRule rule : rules) {
            RuleEvaluator evaluator = evaluators.get(rule.getType());
            if (evaluator == null) {
                log.warn("No hay evaluador registrado para el tipo: {}", rule.getType());
                continue;
            }

            // Extraer valores del campo de todas las filas
            List<Object> values = extractFieldValues(batch, rule.getFieldName());

            FieldQualityResult result = evaluator.evaluate(values, rule);
            results.add(result);

            log.debug("Regla evaluada - field={}, type={}, passRate={:.2f}%",
                    rule.getFieldName(), rule.getType(),
                    result.getPassRate() * 100);
        }

        QualityReport report = new QualityReport(
            executionId, stepId,
            batch.getDatasetName(),
            batch.getRowCount(),
            threshold,
            results
        );

        if (report.passed()) {
            log.info("Calidad OK - dataset={}, overallPassRate={:.2f}%",
                    batch.getDatasetName(), report.overallPassRate() * 100);
        } else {
            log.warn("Calidad FALLO - dataset={}, fallingFields={}",
                    batch.getDatasetName(),
                    report.getFailingFields().stream()
                          .map(FieldQualityResult::getFieldName)
                          .toList());
        }

        return report;
    }

    private List<Object> extractFieldValues(DataBatch batch, String fieldName) {
        List<Object> values = new ArrayList<>();
        for (Map<String, Object> row : batch.getRows()) {
            values.add(row.get(fieldName));
        }
        return values;
    }
}