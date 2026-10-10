package com.taskmanagement.api.infra.cache;

import com.taskmanagement.api.domain.contract.cache.TaskCache;
import com.taskmanagement.api.domain.model.Task;

import java.util.Optional;

/**
 * Implementação Null-Object (No-Op) de {@link TaskCache} utilizada quando
 * o cache distribuído (Redis) está desabilitado.
 */
public class NoOpTaskCache implements TaskCache {

    @Override
    public Optional<Task> get(String id) {
        return Optional.empty();
    }

    @Override
    public void put(Task task) {
        // No-op
    }

    @Override
    public void evict(String id) {
        // No-op
    }
}
