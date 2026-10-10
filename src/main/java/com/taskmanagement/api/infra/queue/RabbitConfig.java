package com.taskmanagement.api.infra.queue;

/**
 * Configuração de conexão com o broker RabbitMQ (camada de infraestrutura).
 */
public record RabbitConfig(
        String host,
        int port,
        String username,
        String password,
        int connectionTimeoutMs) {

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
        String host = envOrDefault("RABBITMQ_HOST", "localhost");
        int port = Integer.parseInt(envOrDefault("RABBITMQ_PORT", "5672"));
        String user = envOrDefault("RABBITMQ_USER", "guest");
        String pass = envOrDefault("RABBITMQ_PASSWORD", "guest");
        return new RabbitConfig(host, port, user, pass, 5000);
    }

    private static String envOrDefault(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val.trim() : defaultValue;
    }
}
