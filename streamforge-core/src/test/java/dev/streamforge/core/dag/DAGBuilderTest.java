package dev.streamforge.core.dag;

import dev.streamforge.core.dag.exception.UnknownDependencyException;
import dev.streamforge.core.model.PipelineDefinition;
import dev.streamforge.core.model.StepDefinition;
import dev.streamforge.core.model.StepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DAGBuilder - construccion del grafo de dependencias")
class DAGBuilderTest {

    private DAGBuilder builder;

    @BeforeEach
    void setUp() { builder = new DAGBuilder(); }

    @Test
    @DisplayName("pipeline sin dependencias genera DAG con todos los nodos como raiz")
    void sinDependencias_todosNodosRaiz() {
        PipelineDefinition pipeline = buildPipeline(
            step("a", StepType.SOURCE),
            step("b", StepType.SOURCE),
            step("c", StepType.SINK)
        );

        PipelineDAG dag = builder.build(pipeline);

        assertEquals(3, dag.getNodeCount());
        assertEquals(3, dag.getRootNodes().size());
    }

    @Test
    @DisplayName("dependencia simple genera arista correcta en el grafo")
    void dependenciaSimple_arista() {
        PipelineDefinition pipeline = buildPipeline(
            step("source", StepType.SOURCE),
            stepWithDeps("sink", StepType.SINK, "source")
        );

        PipelineDAG dag = builder.build(pipeline);

        DAGNode source = dag.getNode("source").orElseThrow();
        DAGNode sink   = dag.getNode("sink").orElseThrow();

        assertEquals(0, source.getInDegree(), "source no tiene dependencias");
        assertEquals(1, sink.getInDegree(),   "sink depende de source");
        assertTrue(source.getSuccessors().contains(sink));
        assertTrue(sink.getPredecessors().contains(source));
    }

    @Test
    @DisplayName("dependencia hacia paso inexistente lanza UnknownDependencyException")
    void dependenciaInexistente_lanzaExcepcion() {
        PipelineDefinition pipeline = buildPipeline(
            stepWithDeps("sink", StepType.SINK, "paso-que-no-existe")
        );

        UnknownDependencyException ex = assertThrows(
            UnknownDependencyException.class,
            () -> builder.build(pipeline)
        );

        assertEquals("sink", ex.getStepId());
        assertEquals("paso-que-no-existe", ex.getUnknownDependency());
    }

    @Test
    @DisplayName("pipeline del documento demo tiene estructura correcta")
    void pipelineDemo_estructuraCorrecta() {
        PipelineDefinition pipeline = buildPipeline(
            step("extract-orders",    StepType.SOURCE),
            step("extract-customers", StepType.SOURCE),
            stepWithDeps("join",      StepType.TRANSFORM, "extract-orders", "extract-customers"),
            stepWithDeps("filter",    StepType.TRANSFORM, "join"),
            stepWithDeps("warehouse", StepType.SINK,      "filter"),
            stepWithDeps("kafka",     StepType.SINK,      "filter")
        );

        PipelineDAG dag = builder.build(pipeline);

        assertEquals(6, dag.getNodeCount());
        assertEquals(2, dag.getRootNodes().size()); // extract-orders y extract-customers

        DAGNode join = dag.getNode("join").orElseThrow();
        assertEquals(2, join.getInDegree()); // depende de 2 pasos

        DAGNode filter = dag.getNode("filter").orElseThrow();
        assertEquals(2, filter.getSuccessors().size()); // warehouse y kafka son sucesores
    }

    @Test
    @DisplayName("pipeline vacio genera DAG vacio sin errores")
    void pipelineVacio_dagVacio() {
        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setId("empty");
        pipeline.setSteps(List.of());

        PipelineDAG dag = builder.build(pipeline);

        assertEquals(0, dag.getNodeCount());
        assertTrue(dag.getRootNodes().isEmpty());
    }

    //Helpers
    private PipelineDefinition buildPipeline(StepDefinition... steps) {
        PipelineDefinition p = new PipelineDefinition();
        p.setId("test-pipeline");
        p.setSteps(List.of(steps));
        return p;
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