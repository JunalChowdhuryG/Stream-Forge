package dev.streamforge.connectors.api;

import dev.streamforge.core.model.DataBatch;
import dev.streamforge.core.model.execution.ExecutionContext;

/**
 * Contrato que deben implementar todos los conectores de StreamForge.
 *
 * Un conector puede actuar como SOURCE (lectura), SINK (escritura) o ambos.
 * La implementacion decide que operaciones soporta y lanza
 * UnsupportedOperationException para las que no.
 *
 * Extensibilidad: un nuevo conector solo necesita implementar esta interfaz
 * y registrarse en ConnectorRegistry. No se modifica el engine.
 */
public interface Connector {

    /**
     * Tipo del conector - debe coincidir con el campo 'connector' en el YAML.
     * Ejemplos: "csv", "json", "postgres", "kafka"
     */
    String getType();

    /**
     * Lee datos de la fuente y los retorna como DataBatch.
     * El schema se detecta automaticamente o se valida contra el declarado.
     *
     * @param config      configuracion especifica del conector
     * @param ctx         contexto de la ejecucion actual
     * @return DataBatch con los datos leidos y el schema detectado
     */
    default DataBatch read(ConnectorConfig config, ExecutionContext ctx) {
        throw new UnsupportedOperationException(
            "El conector '" + getType() + "' no soporta lectura (SOURCE)");
    }

    /**
     * Escribe un DataBatch al destino.
     *
     * @param batch  datos a escribir
     * @param config configuracion especifica del conector
     * @param ctx    contexto de la ejecucion actual
     * @return WriteResult con filas escritas, rechazadas y duracion
     */
    default WriteResult write(DataBatch batch, ConnectorConfig config,
                               ExecutionContext ctx) {
        throw new UnsupportedOperationException(
            "El conector '" + getType() + "' no soporta escritura (SINK)");
    }

    /**
     * Verifica que el conector puede conectarse con la configuracion dada.
     * Se llama al validar el pipeline antes de ejecutar.
     */
    ConnectionHealth healthCheck(ConnectorConfig config);
}