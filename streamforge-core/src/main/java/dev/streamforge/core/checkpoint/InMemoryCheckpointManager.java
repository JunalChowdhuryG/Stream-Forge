package dev.streamforge.core.checkpoint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementacion en memoria del CheckpointManager
 * Usada en tests del core y en modos standalone sin PostgreSQL
 */
public class InMemoryCheckpointManager implements CheckpointManager {

    private final ConcurrentHashMap<String, StepCheckpoint> store = new ConcurrentHashMap<>();

    private String key(String executionId, String stepId) {
        return executionId + ":" + stepId;
    }

    @Override
    public void save(StepCheckpoint checkpoint) {
        store.put(key(checkpoint.getExecutionId(), checkpoint.getStepId()), checkpoint);
    }

    @Override
    public Optional<StepCheckpoint> find(String executionId, String stepId) {
        return Optional.ofNullable(store.get(key(executionId, stepId)));
    }

    @Override
    public boolean exists(String executionId, String stepId) {
        return store.containsKey(key(executionId, stepId));
    }

    @Override
    public List<StepCheckpoint> findAll(String executionId) {
        return store.values().stream()
                .filter(c -> c.getExecutionId().equals(executionId))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAll(String executionId) {
        store.entrySet().removeIf(e -> e.getValue().getExecutionId().equals(executionId));
    }
}