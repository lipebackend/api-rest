package com.taskmanagement.api.infra.cache;

import com.taskmanagement.api.domain.model.Task;
import com.taskmanagement.api.domain.service.TaskServiceImpl;
import com.taskmanagement.api.infra.repository.InMemoryTaskRepository;
import com.taskmanagement.api.infra.web.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JedisTaskCacheTest {

    @Test
    @DisplayName("Should fallback gracefully to repository when Redis is offline without failing GET/CREATE/DELETE")
    void shouldFallbackToRepositoryWhenRedisIsOffline() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxWait(Duration.ofMillis(100));

        // Porta 1 local onde o Redis não está rodando
        try (JedisPool unreachablePool = new JedisPool(poolConfig, "127.0.0.1", 1, 100);
                JedisTaskCache cache = new JedisTaskCache(unreachablePool, JsonMapper.create(), 60)) {

            InMemoryTaskRepository repository = new InMemoryTaskRepository();
            TaskServiceImpl service = new TaskServiceImpl(repository, cache);

            // Mesmo com o Redis desligado, createTask, getTaskById, replaceTask e deleteTask devem funcionar pelo banco
            Task created = service.createTask("Sobreviver sem Redis");
            assertThat(created.getId()).isNotBlank();

            Task fetched = service.getTaskById(created.getId());
            assertThat(fetched.getTitle()).isEqualTo("Sobreviver sem Redis");

            assertThatCode(() -> service.deleteTask(created.getId())).doesNotThrowAnyException();
        }
    }
}
