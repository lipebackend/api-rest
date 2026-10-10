package com.taskmanagement.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Connection;
import com.taskmanagement.api.domain.contract.cache.TaskCache;
import com.taskmanagement.api.domain.contract.queue.TaskQueuePublisher;
import com.taskmanagement.api.domain.contract.repository.TaskRepository;
import com.taskmanagement.api.domain.contract.services.TaskService;
import com.taskmanagement.api.domain.service.TaskServiceImpl;
import com.taskmanagement.api.infra.cache.JedisTaskCache;
import com.taskmanagement.api.infra.cache.NoOpTaskCache;
import com.taskmanagement.api.infra.config.ServerConfig;
import com.taskmanagement.api.infra.db.DatabaseConfig;
import com.taskmanagement.api.infra.queue.NoOpTaskQueuePublisher;
import com.taskmanagement.api.infra.queue.RabbitConfig;
import com.taskmanagement.api.infra.queue.RabbitConnectionFactory;
import com.taskmanagement.api.infra.queue.RabbitQueuePublisher;
import com.taskmanagement.api.infra.repository.InMemoryTaskRepository;
import com.taskmanagement.api.infra.repository.PostgresTaskRepository;
import com.taskmanagement.api.infra.web.http.Server;
import com.taskmanagement.api.infra.web.http.handler.HealthHandler;
import com.taskmanagement.api.infra.web.http.handler.TaskHandler;
import com.taskmanagement.api.infra.web.json.JsonMapper;
import com.zaxxer.hikari.HikariDataSource;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException {
        ServerConfig config = ServerConfig.fromEnv(args);
        ObjectMapper objectMapper = JsonMapper.create();
        List<AutoCloseable> managedResources = new ArrayList<>();

        HikariDataSource dataSource = null;
        TaskRepository taskRepository;
        String storageMode = envOrDefault("STORAGE", "memory").toLowerCase();

        if ("postgres".equals(storageMode) || "jdbc".equals(storageMode)) {
            String jdbcUrl = envOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:5432/task_db");
            String dbUser = envOrDefault("DATABASE_USER", "task_user");
            String dbPassword = envOrDefault("DATABASE_PASSWORD", "task_password");

            LOGGER.log(Level.INFO, "Inicializando pool JDBC e migrações Flyway ({0})", jdbcUrl);
            dataSource = DatabaseConfig.createDataSource(jdbcUrl, dbUser, dbPassword);
            managedResources.add(dataSource);
            DatabaseConfig.runMigrations(dataSource);
            taskRepository = new PostgresTaskRepository(dataSource);
        } else {
            LOGGER.log(Level.INFO, "Inicializando repositório em memória (STORAGE={0})", storageMode);
            taskRepository = new InMemoryTaskRepository();
        }

        TaskCache taskCache;
        if (Boolean.parseBoolean(envOrDefault("CACHE_ENABLED", "false"))) {
            String redisHost = envOrDefault("REDIS_HOST", "localhost");
            int redisPort = Integer.parseInt(envOrDefault("REDIS_PORT", "6379"));
            LOGGER.log(Level.INFO, "Habilitando cache Redis em {0}:{1,number,#}", new Object[] { redisHost, redisPort });
            JedisPool jedisPool = new JedisPool(redisHost, redisPort);
            JedisTaskCache jedisCache = new JedisTaskCache(jedisPool, objectMapper);
            managedResources.add(jedisCache);
            taskCache = jedisCache;
        } else {
            taskCache = new NoOpTaskCache();
        }

        TaskQueuePublisher queuePublisher;
        if (Boolean.parseBoolean(envOrDefault("QUEUE_ENABLED", "false"))) {
            try {
                RabbitConfig rabbitConfig = RabbitConfig.fromEnv();
                LOGGER.log(Level.INFO, "Conectando ao RabbitMQ em {0}:{1,number,#}",
                        new Object[] { rabbitConfig.host(), rabbitConfig.port() });
                Connection rabbitConn = RabbitConnectionFactory.builder()
                        .config(rabbitConfig)
                        .connectionName("task-management-api")
                        .build();
                RabbitQueuePublisher rabbitPublisher = new RabbitQueuePublisher(rabbitConn, objectMapper);
                managedResources.add(rabbitPublisher);
                queuePublisher = rabbitPublisher;
            } catch (Exception e) {
                throw new IOException("Falha ao inicializar conexão com RabbitMQ", e);
            }
        } else {
            queuePublisher = new NoOpTaskQueuePublisher();
        }

        TaskService taskService = new TaskServiceImpl(taskRepository, taskCache, queuePublisher);
        TaskHandler taskHandler = new TaskHandler(taskService, objectMapper);
        HealthHandler healthHandler = new HealthHandler(dataSource);

        Server server = new Server(config.host(), config.port());
        server.createContext("/tasks", taskHandler);
        server.createContext("/health", healthHandler);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.log(Level.INFO, "Encerrando servidor e recursos de forma ordenada...");
            server.close();
            for (AutoCloseable resource : managedResources) {
                try {
                    resource.close();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Erro ao fechar recurso no shutdown", e);
                }
            }
            LOGGER.log(Level.INFO, "Servidor finalizado.");
        }, "shutdown-hook"));

        server.start();
    }

    private static String envOrDefault(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val.trim() : defaultValue;
    }
}
