package com.taskmanagement.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Connection;
import com.taskmanagement.api.domain.contract.cache.TaskCache;
import com.taskmanagement.api.domain.contract.queue.TaskQueuePublisher;
import com.taskmanagement.api.domain.contract.repository.TaskRepository;
import com.taskmanagement.api.domain.contract.services.TaskService;
import com.taskmanagement.api.domain.service.TaskServiceImpl;
import com.taskmanagement.api.infra.cache.JedisTaskCache;
import com.taskmanagement.api.infra.config.Config;
import com.taskmanagement.api.infra.config.ServerConfig;
import com.taskmanagement.api.infra.db.DatabaseConfig;
import com.taskmanagement.api.infra.queue.RabbitConfig;
import com.taskmanagement.api.infra.queue.RabbitConnectionFactory;
import com.taskmanagement.api.infra.queue.RabbitQueuePublisher;
import com.taskmanagement.api.infra.repository.PostgresTaskRepository;
import com.taskmanagement.api.infra.web.http.Server;
import com.taskmanagement.api.infra.web.http.handler.HealthHandler;
import com.taskmanagement.api.infra.web.http.handler.TaskHandler;
import com.taskmanagement.api.infra.web.json.JsonMapper;
import com.zaxxer.hikari.HikariDataSource;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException {
        ServerConfig serverConfig = ServerConfig.fromEnv(args);
        ObjectMapper objectMapper = JsonMapper.create();

        // 1. Banco de Dados (PostgreSQL via HikariCP + Flyway)
        String dbUrl = Config.get("DATABASE_URL", "jdbc:postgresql://localhost:5432/task_db");
        String dbUser = Config.get("DATABASE_USER", "task_user");
        String dbPassword = Config.get("DATABASE_PASSWORD", "task_password");

        LOGGER.log(Level.INFO, "Inicializando pool JDBC e migrações Flyway ({0})", dbUrl);

        HikariDataSource dataSource = DatabaseConfig.createDataSource(dbUrl, dbUser, dbPassword);

        DatabaseConfig.runMigrations(dataSource);

        TaskRepository taskRepository = new PostgresTaskRepository(dataSource);

        // 2. Cache (Redis via JedisPool)
        String redisHost = Config.get("REDIS_HOST", "localhost");
        int redisPort = Config.getInt("REDIS_PORT", 6379);
        LOGGER.log(Level.INFO, "Conectando ao pool Redis em {0}:{1,number,#}", new Object[] { redisHost, redisPort });
        JedisPool jedisPool = new JedisPool(redisHost, redisPort);
        TaskCache taskCache = new JedisTaskCache(jedisPool, objectMapper);

        // 3. Fila de Eventos (RabbitMQ)
        RabbitConfig rabbitConfig = RabbitConfig.fromEnv();
        LOGGER.log(Level.INFO, "Conectando ao RabbitMQ em {0}:{1,number,#}",
                new Object[] { rabbitConfig.host(), rabbitConfig.port() });
        Connection rabbitConn;
        try {
            rabbitConn = RabbitConnectionFactory.builder()
                    .config(rabbitConfig)
                    .connectionName("task-management-api")
                    .build();
        } catch (Exception e) {
            throw new IOException("Falha ao inicializar conexão com RabbitMQ", e);
        }
        TaskQueuePublisher queuePublisher = new RabbitQueuePublisher(rabbitConn, objectMapper);

        // 4. Domínio & Handlers HTTP
        TaskService taskService = new TaskServiceImpl(taskRepository, taskCache, queuePublisher);
        TaskHandler taskHandler = new TaskHandler(taskService, objectMapper);
        HealthHandler healthHandler = new HealthHandler(dataSource);

        // 5. Servidor HTTP
        Server server = new Server(serverConfig.host(), serverConfig.port());
        server.createContext("/tasks", taskHandler);
        server.createContext("/health", healthHandler);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.log(Level.INFO, "Encerrando servidor e recursos de forma ordenada...");
            server.close();
            try {
                ((AutoCloseable) queuePublisher).close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao fechar publisher RabbitMQ", e);
            }
            try {
                ((AutoCloseable) taskCache).close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao fechar cache Redis", e);
            }
            dataSource.close();
            LOGGER.log(Level.INFO, "Servidor finalizado.");
        }, "shutdown-hook"));

        server.start();
    }
}
