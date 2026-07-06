package dev.streamforge.core.chaos;

import dev.streamforge.core.dag.*;
import dev.streamforge.core.dag.exception.CyclicDependencyException;
import dev.streamforge.core.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P1 — Chaos: dependencia circular en el DAG")
class CyclicDependencyChaosTest {

    @Test
    @DisplayName("ciclo directo A→B→A es detectado con el camino completo")
    void cicloDirecto_detectadoConCamino() {
        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setId("ciclo-test");

        StepDefinition a = step("step-a", StepType.TRANSFORM, "step-b");
        StepDefinition b = step("step-b", StepType.TRANSFORM, "step-a");
        pipeline.setSteps(List.of(a, b));

        DAGBuilder builder = new DAGBuilder();
        PipelineDAG dag = builder.build(pipeline);

        CyclicDependencyException ex = assertThrows(
            CyclicDependencyException.class,
            () -> new TopologicalSorter().sort(dag)
        );

        assertFalse(ex.getCycle().isEmpty(),
            "El ciclo debe contener los pasos involucrados");
        assertTrue(ex.getMessage().contains("Dependencia circular"),
            "El mensaje debe indicar dependencia circular");
        assertTrue(ex.getCycle().contains("step-a") || ex.getCycle().contains("step-b"),
            "El ciclo debe nombrar los pasos involucrados");
    }

    @Test
    @DisplayName("ciclo de tres pasos A→B→C→A incluye los tres en el mensaje")
    void cicloTresPasos_incluyeTodosLosNodos() {
        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setId("ciclo-tres");

        StepDefinition a = step("step-a", StepType.TRANSFORM, "step-c");
        StepDefinition b = step("step-b", StepType.TRANSFORM, "step-a");
        StepDefinition c = step("step-c", StepType.TRANSFORM, "step-b");
        pipeline.setSteps(List.of(a, b, c));

        DAGBuilder builder = new DAGBuilder();
        PipelineDAG dag = builder.build(pipeline);

        CyclicDependencyException ex = assertThrows(
            CyclicDependencyException.class,
            () -> new TopologicalSorter().sort(dag)
        );

        assertTrue(ex.getCycle().size() >= 3,
            "El ciclo debe incluir al menos los 3 pasos: " + ex.getCycle());
    }

    @Test
    @DisplayName("pipeline sin ciclos no lanza excepcion")
    void sinCiclo_noLanzaExcepcion() {
        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setId("sin-ciclo");

        StepDefinition a = step("a", StepType.SOURCE);
        StepDefinition b = step("b", StepType.TRANSFORM, "a");
        StepDefinition c = step("c", StepType.SINK,      "b");
        pipeline.setSteps(List.of(a, b, c));

        DAGBuilder builder = new DAGBuilder();
        PipelineDAG dag    = builder.build(pipeline);

        assertDoesNotThrow(() -> new TopologicalSorter().sort(dag),
            "Pipeline sin ciclos no debe lanzar excepcion");
    }

    //Helper
    private StepDefinition step(String id, StepType type, String... deps) {
        StepDefinition s = new StepDefinition();
        s.setId(id);
        s.setType(type);
        if (deps.length > 0) s.setDependsOn(List.of(deps));
        return s;
    }
}