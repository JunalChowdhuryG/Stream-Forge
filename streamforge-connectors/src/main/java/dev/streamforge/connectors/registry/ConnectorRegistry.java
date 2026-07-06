package dev.streamforge.connectors.registry;

import dev.streamforge.connectors.api.Connector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registro central de conectores disponibles.
 *
 * Los conectores se registran al inicializar el engine.
 * El PipelineExecutor resuelve el conector correcto por tipo
 * al procesar cada paso SOURCE o SINK del pipeline.
 */
public class ConnectorRegistry {

    private final Map<String, Connector> connectors = new HashMap<>();

    /**
     * Registra un conector. Si ya existe uno con el mismo tipo, lo sobreescribe.
     */
    public void register(Connector connector) {
        connectors.put(connector.getType().toLowerCase(), connector);
    }

    /**
     * Resuelve un conector por tipo.
     *
     * @param type tipo del conector (ej: "csv", "postgres")
     * @return el conector registrado, si existe
     */
    public Optional<Connector> resolve(String type) {
        return Optional.ofNullable(connectors.get(type.toLowerCase()));
    }

    /**
     * Resuelve un conector por tipo o lanza excepcion si no existe.
     */
    public Connector resolveOrThrow(String type) {
        return resolve(type).orElseThrow(() ->
            new IllegalArgumentException(
                "No hay conector registrado para el tipo: '" + type + "'. "
                + "Tipos disponibles: " + connectors.keySet()
            )
        );
    }

    public Collection<Connector> getAll() {
        return connectors.values();
    }

    public boolean isRegistered(String type) {
        return connectors.containsKey(type.toLowerCase());
    }
}