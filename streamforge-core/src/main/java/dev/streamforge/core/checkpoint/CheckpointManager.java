package dev.streamforge.core.checkpoint;

import java.util.List;
import java.util.Optional;

/**
 * Interfaz para persistencia y consulta de checkpoints de pasos
 *
 * La implementacion concreta (PostgresCheckpointManager) vive en
 * streamforge-engine donde Spring y JDBC estan disponibles
 *
 * Implementacion NOOP disponible para tests del core sin base de datos
 */
public interface CheckpointManager {

    /**
     * Persiste un checkpoint para un paso completado
     */
    void save(StepCheckpoint checkpoint);

    /**
     * Busca el checkpoint de un paso especifico en una ejecucion
     */
    Optional<StepCheckpoint> find(String executionId, String stepId);

    /**
     * Verifica si un paso ya tiene checkpoint en esta ejecucion
     */
    boolean exists(String executionId, String stepId);

    /**
     * Retorna todos los checkpoints de una ejecucion
     * Usado para mostrar el progreso de reanudacion
     */
    List<StepCheckpoint> findAll(String executionId);

    /**
     * Elimina todos los checkpoints de una ejecucion.
     * Usado cuando se inicia una ejecucion completamente nueva
     */
    void deleteAll(String executionId);

    /**
     * Implementacion nula para tests del core sin base de datos
     */
    CheckpointManager NOOP = new CheckpointManager() {
        public void save(StepCheckpoint c)                          {}
        public Optional<StepCheckpoint> find(String e, String s)   { return Optional.empty(); }
        public boolean exists(String e, String s)                   { return false; }
        public List<StepCheckpoint> findAll(String e)               { return List.of(); }
        public void deleteAll(String e)                             {}
    };

    /**
     * Implementacion en memoria para tests de integracion del core
     */
    static CheckpointManager inMemory() {
        return new InMemoryCheckpointManager();
    }
}