package dev.streamforge.core.dag;

import dev.streamforge.core.model.PipelineDefinition;
import dev.streamforge.core.model.StepType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PipelineDefinition - carga desde YAML")
class PipelineDefinitionTest {

    @Test
    @DisplayName("carga correctamente el pipeline de demo desde YAML")
    void cargaDesdeYaml_correcto() throws Exception {
        InputStream yaml = getClass().getClassLoader()
                .getResourceAsStream("pipelines/orders-daily-etl.yml");

        assertNotNull(yaml, "El archivo YAML de demo debe existir en resources");

        PipelineDefinition pipeline = PipelineDefinition.fromYaml(yaml);

        assertEquals("orders-daily-etl", pipeline.getId());
        assertEquals(6, pipeline.getSteps().size());

        // Verificar que el join tiene las dependencias correctas
        var join = pipeline.getSteps().stream()
                .filter(s -> s.getId().equals("join-orders-customers"))
                .findFirst()
                .orElseThrow();

        assertEquals(StepType.TRANSFORM, join.getType());
        assertEquals(2, join.getDependsOn().size());
        assertTrue(join.getDependsOn().contains("extract-orders"));
        assertTrue(join.getDependsOn().contains("extract-customers"));

        // Verificar que el join tiene reglas de calidad
        assertNotNull(join.getQuality());
        assertFalse(join.getQuality().getRules().isEmpty());
    }
}