package com.taskmanagement.api.infra.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanagement.api.domain.contract.cache.TaskCache;
import com.taskmanagement.api.domain.model.Task;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public class JedisTaskCache implements TaskCache {

    private static final Logger LOGGER = Logger.getLogger(JedisTaskCache.class.getName());
    private static final String KEY_PREFIX = "task:";
    private static final long DEFAULT_TTL = 3600;

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    public JedisTaskCache(JedisPool jedisPool, ObjectMapper objectMapper) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    @Override
    public Optional<Task> get(String id) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(keyFor(id));
            if (json == null) {
                return Optional.empty();
            }
            CachedTask cachedTask = objectMapper.readValue(json, CachedTask.class);
            return Optional.of(cachedTask.toTask());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not deserialize task from Redis for id: " + id, exception);
        }
    }

    @Override
    public void put(Task task) {
        Objects.requireNonNull(task, "task cannot be null");
        try (Jedis jedis = jedisPool.getResource()) {
            String json = objectMapper.writeValueAsString(CachedTask.from(task));
            jedis.setex(keyFor(task.getId()), DEFAULT_TTL, json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize task for Redis: " + task.getId(), exception);
        }
    }

    @Override
    public void evict(String id) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(keyFor(id));
        }
    }

    private static String keyFor(String id) {
        Objects.requireNonNull(id, "id cannot be null");
        return KEY_PREFIX + id;
    }

    private record CachedTask(String id, String title, boolean completed, Instant createdAt) {
        private static CachedTask from(Task task) {
            return new CachedTask(task.getId(), task.getTitle(), task.isCompleted(), task.getCreatedAt());
        }
        private Task toTask() {
            return new Task(id, title, completed, createdAt);
        }
    }
}
