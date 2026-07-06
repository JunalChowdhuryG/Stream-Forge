package dev.streamforge.quality.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.streamforge.quality.model.QualityRule;
import dev.streamforge.quality.model.RuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Carga reglas de calidad desde archivos YAML.
 *
 * Formato esperado:
 *   dataset: nombre_dataset
 *   threshold: 0.95
 *   rules:
 *     - field: campo
 *       checks:
 *         - type: NOT_NULL
 *         - type: RANGE
 *           min: 0
 *           max: 100
 */
public class QualityRuleLoader {

    private static final Logger log = LoggerFactory.getLogger(QualityRuleLoader.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    public record QualityConfig(
        String dataset,
        double threshold,
        List<QualityRule> rules
    ) {}

    /**
     * Carga la configuracion de calidad desde un archivo YAML.
     */
    @SuppressWarnings("unchecked")
    public QualityConfig load(Path yamlPath) throws IOException {
        return load(Files.newInputStream(yamlPath));
    }

    @SuppressWarnings("unchecked")
    public QualityConfig load(InputStream stream) throws IOException {
        Map<String, Object> raw = YAML.readValue(stream, Map.class);

        String dataset   = (String) raw.get("dataset");
        double threshold = raw.containsKey("threshold")
                ? Double.parseDouble(raw.get("threshold").toString())
                : 0.95;

        List<QualityRule> rules = new ArrayList<>();
        List<Map<String, Object>> fieldConfigs =
                (List<Map<String, Object>>) raw.get("rules");

        if (fieldConfigs != null) {
            for (Map<String, Object> fieldConfig : fieldConfigs) {
                String fieldName = (String) fieldConfig.get("field");
                List<Map<String, Object>> checks =
                        (List<Map<String, Object>>) fieldConfig.get("checks");

                if (checks != null) {
                    for (Map<String, Object> check : checks) {
                        String typeStr = (String) check.get("type");
                        RuleType ruleType = RuleType.valueOf(typeStr);

                        Map<String, Object> params = new HashMap<>(check);
                        params.remove("type");

                        rules.add(new QualityRule(fieldName, ruleType, params));
                    }
                }
            }
        }

        log.info("Reglas cargadas — dataset={}, threshold={}, rules={}",
                dataset, threshold, rules.size());

        return new QualityConfig(dataset, threshold, rules);
    }
}