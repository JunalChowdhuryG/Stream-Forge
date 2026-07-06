package dev.streamforge.connectors.api;

/**
 * Resultado de una operacion de escritura de un conector SINK.
 */
public record WriteResult(
    long rowsWritten,
    long rowsRejected,
    String destination,
    long durationMs
) {
    public boolean isSuccessful() { return rowsRejected == 0; }

    public static WriteResult of(long written, long rejected,
                                  String destination, long durationMs) {
        return new WriteResult(written, rejected, destination, durationMs);
    }
}