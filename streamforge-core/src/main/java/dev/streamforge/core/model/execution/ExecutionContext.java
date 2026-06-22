package dev.streamforge.core.model.execution;

import dev.streamforge.core.model.DataBatch;
import dev.streamforge.core.model.PipelineDefinition;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contexto compartido durante la ejecucion de un pipeline
 * Contiene:
 *   - El executionId unico de esta ejecucion
 *   - Los parametros de ejecucion (ej: fecha de proceso)
 *   - Los DataBatch producidos por cada paso (outputs intermedios)
 *   - La definicion del pipeline en ejecucion
 * Thread-safe: los outputs se almacenan en ConcurrentHashMap
 */
public class ExecutionContext {

    private final String executionId;
    private final PipelineDefinition pipeline;
    private final Map<String, String> parameters;
    private final ConcurrentHashMap<String, DataBatch> stepOutputs;
    private final long startedAtMs;

    public ExecutionContext(String executionId,
                            PipelineDefinition pipeline,
                            Map<String, String> parameters) {
        this.executionId  = executionId;
        this.pipeline     = pipeline;
        this.parameters   = Map.copyOf(parameters);
        this.stepOutputs  = new ConcurrentHashMap<>();
        this.startedAtMs  = System.currentTimeMillis();
    }

    /**
     * Registra el output de un paso para que los pasos sucesores puedan leerlo
     */
    public void setStepOutput(String datasetName, DataBatch batch) {
        stepOutputs.put(datasetName, batch);
    }

    /**
     * Obtiene el output de un paso previo por nombre de dataset
     */
    public Optional<DataBatch> getStepOutput(String datasetName) {
        return Optional.ofNullable(stepOutputs.get(datasetName));
    }

    /**
     * Resuelve un parametro de ejecucion
     * Soporta variables como ${execution.date}
     */
    public String resolveParam(String key) {
        if ("execution.date".equals(key)) {
            return parameters.getOrDefault("date", LocalDate.now().toString());
        }
        return parameters.getOrDefault(key, "");
    }

    public String getExecutionId()               { return executionId;           }
    public PipelineDefinition getPipeline()      { return pipeline;              }
    public Map<String, String> getParameters()   { return parameters;            }
    public long getElapsedMs()                   { return System.currentTimeMillis() - startedAtMs; }

    public Map<String, DataBatch> getAllOutputs() {
        return Collections.unmodifiableMap(stepOutputs);
    }
}