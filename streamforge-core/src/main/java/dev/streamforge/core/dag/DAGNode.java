package dev.streamforge.core.dag;

import dev.streamforge.core.model.StepDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Nodo en el DAG de ejecucion del pipeline
 * Envuelve un StepDefinition con la informacion de conectividad del grafo
 */
public class DAGNode {

    private final StepDefinition step;
    private final List<DAGNode> successors;   // pasos que dependen de este
    private final List<DAGNode> predecessors; // pasos de los que este depende
    private int inDegree;

    public DAGNode(StepDefinition step) {
        this.step         = step;
        this.successors   = new ArrayList<>();
        this.predecessors = new ArrayList<>();
        this.inDegree     = 0;
    }

    public StepDefinition getStep()         { return step;         }
    public String getStepId()               { return step.getId(); }
    public List<DAGNode> getSuccessors()    { return successors;   }
    public List<DAGNode> getPredecessors()  { return predecessors; }
    public int getInDegree()                { return inDegree;     }

    public void addSuccessor(DAGNode node) {
        successors.add(node);
    }

    public void addPredecessor(DAGNode node) {
        predecessors.add(node);
        inDegree++;
    }

    public void decrementInDegree() { inDegree--; }

    @Override
    public String toString() {
        return "DAGNode{stepId='" + getStepId() + "', inDegree=" + inDegree + "}";
    }
}