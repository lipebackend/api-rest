package com.taskmanagement.api.infra.config;

public record ServerConfig(String host, int port) {
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 8080;
    // Construtor compacto do Record (Fail-Fast)
    public ServerConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host não pode ser vazio");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Porta inválida: " + port + ". Deve estar entre 1 e 65535.");
        }
    }
    public static ServerConfig fromEnv(String[] args) {
        String envHost = System.getenv("HOST");
        String host = (envHost != null && !envHost.isBlank()) ? envHost.trim() : DEFAULT_HOST;
        String rawPort = (args != null && args.length > 0 && args[0] != null && !args[0].isBlank())
                ? args[0].trim()
                : System.getenv("PORT");
        int port = (rawPort != null && !rawPort.isBlank())
                ? Integer.parseInt(rawPort.trim())
                : DEFAULT_PORT;
        return new ServerConfig(host, port);
    }
}
