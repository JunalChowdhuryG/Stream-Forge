package dev.streamforge.connectors.api;

/**
 * Estado de salud de la conexion de un conector.
 */
public record ConnectionHealth(
    boolean healthy,
    String connectorType,
    String message,
    long latencyMs
) {
    public static ConnectionHealth ok(String type, long latencyMs) {
        return new ConnectionHealth(true, type, "OK", latencyMs);
    }

    public static ConnectionHealth failed(String type, String reason) {
        return new ConnectionHealth(false, type, reason, -1);
    }
}