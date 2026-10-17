package com.taskmanagement.api.infra.queue;

import com.taskmanagement.api.infra.config.Config;

/**
 * Configuração de conexão com o broker RabbitMQ (camada de infraestrutura).
 */
public record RabbitConfig(
        String host,
        int port,
        String username,
        String password,
        int connectionTimeoutMs) {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5672;
    private static final String DEFAULT_USER = "guest";
    private static final String DEFAULT_PASSWORD = "guest";
    private static final int DEFAULT_TIMEOUT_MS = 5000;

    public RabbitConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("RabbitMQ host não pode ser vazio");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Porta inválida para RabbitMQ: " + port);
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("RabbitMQ username não pode ser vazio");
        }
        if (password == null) {
            throw new IllegalArgumentException("RabbitMQ password não pode ser nulo");
        }
        if (connectionTimeoutMs <= 0) {
            throw new IllegalArgumentException("connectionTimeoutMs deve ser positivo");
        }
    }

    public static RabbitConfig fromEnv() {
        String host = Config.get("RABBITMQ_HOST", DEFAULT_HOST);
        int port = Config.getInt("RABBITMQ_PORT", DEFAULT_PORT);
        String user = Config.get("RABBITMQ_USER", DEFAULT_USER);
        String pass = Config.get("RABBITMQ_PASSWORD", DEFAULT_PASSWORD);
        return new RabbitConfig(host, port, user, pass, DEFAULT_TIMEOUT_MS);
    }
}
