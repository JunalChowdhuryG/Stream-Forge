package dev.streamforge.core.dag.property;

import dev.streamforge.core.dag.DAGBuilder;
import dev.streamforge.core.dag.DAGNode;
import dev.streamforge.core.dag.PipelineDAG;
import dev.streamforge.core.dag.TopologicalSorter;
import dev.streamforge.core.dag.exception.CyclicDependencyException;
import dev.streamforge.core.model.PipelineDefinition;
import dev.streamforge.core.model.StepDefinition;
import dev.streamforge.core.model.StepType;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Property-based tests para el DAG y el ordenamiento topologico
 * Verifica invariantes que deben cumplirse para cualquier pipeline valido
 */
class DAGProperties {

    private final DAGBuilder        builder = new DAGBuilder();
    private final TopologicalSorter sorter  = new TopologicalSorter();

    @Property(tries = 200)
    @Label("Propiedad 1: para cualquier DAG aciclico, Kahn procesa todos los nodos")
    void prop_dagAciclico_procesaTodosLosNodos(
            @ForAll("linearPipelines") PipelineDefinition pipeline) {

        PipelineDAG dag = builder.build(pipeline);
        var result = sorter.sort(dag);

        assertEquals(pipeline.getSteps().size(), result.getTotalNodes(),
            "Kahn debe procesar exactamente el mismo numero de nodos que pasos");
    }

    @Property(tries = 200)
    @Label("Propiedad 2: en el orden plano, todo paso aparece despues de sus dependencias")
    void prop_ordenPlano_respetaDependencias(
            @ForAll("linearPipelines") PipelineDefinition pipeline) {

        PipelineDAG dag    = builder.build(pipeline);
        var result         = sorter.sort(dag);
        var flatOrder      = result.flatOrder().stream().map(DAGNode::getStepId).toList();

        for (StepDefinition step : pipeline.getSteps()) {
            int stepIdx = flatOrder.indexOf(step.getId());
            for (String dep : step.getDependsOn()) {
                int depIdx = flatOrder.indexOf(dep);
                assertTrue(depIdx < stepIdx,
                    "Dependencia '" + dep + "' debe aparecer antes que '" + step.getId() + "'");
            }
        }
    }

    @Property(tries = 100)
    @Label("Propiedad 3: pasos en el mismo nivel no tienen dependencia entre si")
    void prop_mismoNivel_sinDependenciaEntreSi(
            @ForAll("linearPipelines") PipelineDefinition pipeline) {

        PipelineDAG dag = builder.build(pipeline);
        var result      = sorter.sort(dag);

        for (List<DAGNode> level : result.levels()) {
            for (int i = 0; i < level.size(); i++) {
                for (int j = i + 1; j < level.size(); j++) {
                    DAGNode a = level.get(i);
                    DAGNode b = level.get(j);

                    boolean aDepB = a.getPredecessors().contains(b);
                    boolean bDepA = b.getPredecessors().contains(a);

                    assertFalse(aDepB || bDepA,
                        "Pasos en el mismo nivel no deben tener dependencia entre si: "
                        + a.getStepId() + " y " + b.getStepId());
                }
            }
        }
    }

    //Proveedores de datos
    @Provide
    Arbitrary<PipelineDefinition> linearPipelines() {
        // Genera pipelines lineales (cadena A -> B -> C -> ...) sin ciclos
        return Arbitraries.integers().between(1, 8).map(n -> {
            PipelineDefinition p = new PipelineDefinition();
            p.setId("prop-test-pipeline");

            List<StepDefinition> steps = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                StepDefinition s = new StepDefinition();
                s.setId("step-" + i);
                s.setType(i == 0 ? StepType.SOURCE : (i == n-1 ? StepType.SINK : StepType.TRANSFORM));
                if (i > 0) s.setDependsOn(List.of("step-" + (i-1)));
                steps.add(s);
            }
            p.setSteps(steps);
            return p;
        });
    }

    // Assertions helpers para jqwik
    private void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) throw new AssertionError(msg + " (expected " + expected + ", got " + actual + ")");
    }
    private void assertTrue(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }
    private void assertFalse(boolean condition, String msg) {
        if (condition) throw new AssertionError(msg);
    }
}