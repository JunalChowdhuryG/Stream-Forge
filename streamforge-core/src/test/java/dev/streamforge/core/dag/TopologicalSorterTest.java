package dev.streamforge.core.dag;

import dev.streamforge.core.dag.exception.CyclicDependencyException;
import dev.streamforge.core.model.PipelineDefinition;
import dev.streamforge.core.model.StepDefinition;
import dev.streamforge.core.model.StepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TopologicalSorter - ordenamiento topologico con Kahn")
class TopologicalSorterTest {

    private DAGBuilder        builder;
    private TopologicalSorter sorter;

    @BeforeEach
    void setUp() {
        builder = new DAGBuilder();
        sorter  = new TopologicalSorter();
    }

    @Test
    @DisplayName("pipeline lineal produce un nivel por paso")
    void pipelineLineal_unNivelPorPaso() {
        PipelineDAG dag = buildAndSort(
            step("a", StepType.SOURCE),
            stepWithDeps("b", StepType.TRANSFORM, "a"),
            stepWithDeps("c", StepType.SINK, "b")
        );

        var result = sorter.sort(dag);

        assertEquals(3, result.getLevelCount());
        assertEquals(3, result.getTotalNodes());
        assertEquals("a", result.levels().get(0).get(0).getStepId());
        assertEquals("b", result.levels().get(1).get(0).getStepId());
        assertEquals("c", result.levels().get(2).get(0).getStepId());
    }

    @Test
    @DisplayName("pasos independientes quedan en el mismo nivel de paralelismo")
    void pasosIndependientes_mismoNivel() {
        PipelineDAG dag = buildAndSort(
            step("source-a", StepType.SOURCE),
            step("source-b", StepType.SOURCE),
            stepWithDeps("join", StepType.TRANSFORM, "source-a", "source-b"),
            stepWithDeps("sink-1", StepType.SINK, "join"),
            stepWithDeps("sink-2", StepType.SINK, "join")
        );

        var result = sorter.sort(dag);

        // Nivel 0: source-a y source-b (paralelo)
        assertEquals(2, result.levels().get(0).size());
        // Nivel 1: join
        assertEquals(1, result.levels().get(1).size());
        // Nivel 2: sink-1 y sink-2 (paralelo)
        assertEquals(2, result.levels().get(2).size());
    }

    @Test
    @DisplayName("pipeline del demo produce 4 niveles correctos")
    void pipelineDemo_cuatroNiveles() {
        PipelineDAG dag = buildAndSort(
            step("extract-orders",    StepType.SOURCE),
            step("extract-customers", StepType.SOURCE),
            stepWithDeps("join",    StepType.TRANSFORM, "extract-orders", "extract-customers"),
            stepWithDeps("filter",  StepType.TRANSFORM, "join"),
            stepWithDeps("warehouse", StepType.SINK,    "filter"),
            stepWithDeps("kafka",     StepType.SINK,    "filter")
        );

        var result = sorter.sort(dag);

        assertEquals(4, result.getLevelCount());
        assertEquals(2, result.levels().get(0).size()); // extracts en paralelo
        assertEquals(1, result.levels().get(1).size()); // join
        assertEquals(1, result.levels().get(2).size()); // filter
        assertEquals(2, result.levels().get(3).size()); // sinks en paralelo
    }

    @Test
    @DisplayName("ciclo directo entre dos pasos lanza CyclicDependencyException")
    void cicloDirecto_lanzaExcepcion() {
        PipelineDAG dag = buildAndSort(
            stepWithDeps("a", StepType.TRANSFORM, "b"),
            stepWithDeps("b", StepType.TRANSFORM, "a")
        );

        CyclicDependencyException ex = assertThrows(
            CyclicDependencyException.class,
            () -> sorter.sort(dag)
        );

        assertFalse(ex.getCycle().isEmpty());
        assertTrue(ex.getMessage().contains("Dependencia circular"));
    }

    @Test
    @DisplayName("ciclo de tres pasos es detectado con el camino completo")
    void cicloTresPasos_detectadoConCamino() {
        PipelineDAG dag = buildAndSort(
            stepWithDeps("a", StepType.TRANSFORM, "c"),
            stepWithDeps("b", StepType.TRANSFORM, "a"),
            stepWithDeps("c", StepType.TRANSFORM, "b")
        );

        CyclicDependencyException ex = assertThrows(
            CyclicDependencyException.class,
            () -> sorter.sort(dag)
        );

        assertTrue(ex.getCycle().size() >= 3,
            "El ciclo debe incluir al menos los 3 pasos involucrados");
    }

    @Test
    @DisplayName("pipeline con un solo paso genera un nivel con ese paso")
    void unSoloPaso_unNivel() {
        PipelineDAG dag = buildAndSort(step("solo", StepType.SOURCE));

        var result = sorter.sort(dag);

        assertEquals(1, result.getLevelCount());
        assertEquals("solo", result.levels().get(0).get(0).getStepId());
    }

    @Test
    @DisplayName("orden plano contiene todos los nodos exactamente una vez")
    void ordenPlano_contieneTodosLosNodos() {
        PipelineDAG dag = buildAndSort(
            step("a", StepType.SOURCE),
            step("b", StepType.SOURCE),
            stepWithDeps("c", StepType.TRANSFORM, "a", "b"),
            stepWithDeps("d", StepType.SINK, "c")
        );

        var result = sorter.sort(dag);

        assertEquals(4, result.getTotalNodes());
        var ids = result.flatOrder().stream().map(DAGNode::getStepId).toList();
        assertTrue(ids.contains("a"));
        assertTrue(ids.contains("b"));
        assertTrue(ids.contains("c"));
        assertTrue(ids.contains("d"));
        // c debe ir despues de a y b
        assertTrue(ids.indexOf("c") > ids.indexOf("a"));
        assertTrue(ids.indexOf("c") > ids.indexOf("b"));
        // d debe ir despues de c
        assertTrue(ids.indexOf("d") > ids.indexOf("c"));
    }

    //Helpers
    private PipelineDAG buildAndSort(StepDefinition... steps) {
        PipelineDefinition p = new PipelineDefinition();
        p.setId("test");
        p.setSteps(List.of(steps));
        return builder.build(p);
    }

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