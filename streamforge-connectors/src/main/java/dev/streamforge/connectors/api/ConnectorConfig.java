package dev.streamforge.connectors.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuracion de un conector leida desde el YAML del pipeline.
 * Encapsula los parametros especificos de cada tipo de conector.
 */
public class ConnectorConfig {

    private final String type;
    private final Map<String, Object> params;

    public ConnectorConfig(String type, Map<String, Object> params) {
        this.type   = type;
        this.params = new HashMap<>(params != null ? params : Map.of());
    }

    public String getType() { return type; }

    public String getString(String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    public String getString(String key, String defaultValue) {
        String val = getString(key);
        return val != null ? val : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        Object val = params.get(key);
        if (val == null) return defaultValue;
        return Integer.parseInt(val.toString());
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object val = params.get(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val.toString());
    }

    public Map<String, Object> getParams() {
        return Collections.unmodifiableMap(params);
    }

    @Override
    public String toString() {
        return "ConnectorConfig{type='" + type + "', params=" + params + "}";
    }
}