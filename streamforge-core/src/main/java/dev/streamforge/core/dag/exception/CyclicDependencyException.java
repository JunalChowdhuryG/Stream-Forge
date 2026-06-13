package dev.streamforge.core.dag.exception;

import java.util.List;

/**
 * Lanzada cuando el DAGBuilder detecta una dependencia circular
 * en la definicion de pasos del pipeline
 *
 * El mensaje incluye el ciclo exacto para facilitar la correccion del YAML
 */
public class CyclicDependencyException extends RuntimeException {

    private final List<String> cycle;

    public CyclicDependencyException(List<String> cycle) {
        super(buildMessage(cycle));
        this.cycle = List.copyOf(cycle);
    }

    public List<String> getCycle() { return cycle; }

    private static String buildMessage(List<String> cycle) {
        return "Dependencia circular detectada en el pipeline: "
                + String.join(" --> ", cycle)
                + " --> " + cycle.get(0);
    }
}