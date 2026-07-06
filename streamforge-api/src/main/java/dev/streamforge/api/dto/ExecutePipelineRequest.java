package dev.streamforge.api.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Request para disparar la ejecucion de un pipeline.
 */
public class ExecutePipelineRequest {

    private Map<String, String> parameters = new HashMap<>();
    private boolean forceRestart = false;

    public Map<String, String> getParameters() { return parameters;   }
    public void setParameters(Map<String, String> v) { parameters = v; }
    public boolean isForceRestart()            { return forceRestart; }
    public void setForceRestart(boolean v)     { forceRestart = v;    }
}