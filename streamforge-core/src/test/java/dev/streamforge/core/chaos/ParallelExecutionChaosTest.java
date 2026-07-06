package dev.streamforge.core.chaos;

import dev.streamforge.core.dag.*;
import dev.streamforge.core.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P5 - Chaos: paralelismo correcto de pasos independientes")
class ParallelExecutionChaosTest {

    @Test
    @DisplayName("pasos independientes quedan en el mismo nivel de paralelismo")
    void pasosIndependientes_mismoNivel() {
        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setId("parallel-test");

        pipeline.setSteps(List.of(
            step("source", StepType.SOURCE),
            stepWithDeps("join",   StepType.TRANSFORM, "source"),
            stepWithDeps("sink-1", StepType.SINK,      "join"),
            stepWithDeps("sink-2", StepType.SINK,      "join"),
            stepWithDeps("sink-3", StepType.SINK,      "join")
        ));

        var result = new TopologicalSorter().sort(
            new DAGBuilder().build(pipeline));

        List<DAGNode> lastLevel = result.levels().get(result.getLevelCount() - 1);
        assertEquals(3, lastLevel.size(),
            "Los 3 sinks deben estar en el mismo nivel de paralelismo");

        List<String> ids = lastLevel.stream().map(DAGNode::getStepId).toList();
        assertTrue(ids.contains("sink-1"));
        assertTrue(ids.contains("sink-2"));
        assertTrue(ids.contains("sink-3"));
    }

    @Test
    @DisplayName("extracciones independientes se ejecutan en paralelo nivel 0")
    void extracciones_paralelas_nivel0() {
        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setId("parallel-sources");

        pipeline.setSteps(List.of(
            step("extract-orders",    StepType.SOURCE),
            step("extract-customers", StepType.SOURCE),
            step("extract-products",  StepType.SOURCE),
            stepWithDeps("join-all", StepType.TRANSFORM,
                "extract-orders", "extract-customers", "extract-products")
        ));

        var result = new TopologicalSorter().sort(
            new DAGBuilder().build(pipeline));

        List<DAGNode> firstLevel = result.levels().get(0);
        assertEquals(3, firstLevel.size(),
            "Las 3 extracciones deben estar en el nivel 0 de paralelismo");
    }

    //Helpers
    private StepDefinition step(String id, StepType type) {
        StepDefinition s = new StepDefinition();
        s.setId(id);
        s.setType(type);
        return s;
    }

    private StepDefinition stepWithDeps(String id, StepType type, String... deps) {
        StepDefinition s = step(id, type);
        s.setDependsOn(List.of(deps));
        return s;
    }
}