package com.taskmanagement.api.infra.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanagement.api.domain.contract.cache.TaskCache;
import com.taskmanagement.api.domain.model.Task;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementação thread-safe de {@link TaskCache} usando {@link JedisPool}.
 *
 * <p>Decisões técnicas:
 * <ul>
 *   <li>Thread-safety sob Virtual Threads: cada operação obtém uma instância {@link Jedis}
 *       emprestada do {@link JedisPool} via try-with-resources e a devolve imediatamente ao pool.</li>
 *   <li>Tolerância a falhas (Graceful Degradation): falhas de rede/conexão com o Redis
 *       ({@link JedisException}) são registradas em log como {@code WARNING} e nunca derrubam
 *       a requisição HTTP principal — a aplicação faz fallback transparente para o PostgreSQL.</li>
 *   <li>TTL explícito ({@code setex}) para evitar crescimento infinito na memória do Redis.</li>
 * </ul>
 */
public class JedisTaskCache implements TaskCache, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(JedisTaskCache.class.getName());
    private static final String KEY_PREFIX = "task:";
    private static final long DEFAULT_TTL_SECONDS = 3600;

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public JedisTaskCache(JedisPool jedisPool, ObjectMapper objectMapper) {
        this(jedisPool, objectMapper, DEFAULT_TTL_SECONDS);
    }

    public JedisTaskCache(JedisPool jedisPool, ObjectMapper objectMapper, long ttlSeconds) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public Optional<Task> get(String id) {
        String key = keyFor(id);
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(key);
            if (json == null) {
                return Optional.empty();
            }
            CachedTask cachedTask = objectMapper.readValue(json, CachedTask.class);
            return Optional.of(cachedTask.toTask());
        } catch (JedisException exception) {
            LOGGER.log(Level.WARNING, "Redis indisponível ao consultar chave " + key + " (fallback para o banco)", exception);
            return Optional.empty();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING, "Dado corrompido no Redis para a chave " + key + " (ignorando cache)", exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(Task task) {
        Objects.requireNonNull(task, "task cannot be null");
        String key = keyFor(task.getId());
        String json;
        try {
            json = objectMapper.writeValueAsString(CachedTask.from(task));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize task for Redis: " + task.getId(), exception);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(key, ttlSeconds, json);
        } catch (JedisException exception) {
            LOGGER.log(Level.WARNING, "Redis indisponível ao gravar chave " + key, exception);
        }
    }

    @Override
    public void evict(String id) {
        String key = keyFor(id);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        } catch (JedisException exception) {
            LOGGER.log(Level.WARNING, "Redis indisponível ao invalidar chave " + key, exception);
        }
    }

    @Override
    public void close() {
        if (!jedisPool.isClosed()) {
            jedisPool.close();
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
