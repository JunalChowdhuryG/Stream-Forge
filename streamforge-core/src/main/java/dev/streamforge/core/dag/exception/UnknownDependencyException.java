package dev.streamforge.core.dag.exception;

/**
 * Lanzada cuando un paso referencia en depends_on un paso
 * que no existe en la definicion del pipeline
 */
public class UnknownDependencyException extends RuntimeException {

    private final String stepId;
    private final String unknownDependency;

    public UnknownDependencyException(String stepId, String unknownDependency) {
        super("El paso '" + stepId + "' depende de '" + unknownDependency
                + "' que no existe en el pipeline.");
        this.stepId             = stepId;
        this.unknownDependency  = unknownDependency;
    }

    public String getStepId()             { return stepId;            }
    public String getUnknownDependency()  { return unknownDependency; }
}