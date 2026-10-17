package com.taskmanagement.api.infra.config;

public record ServerConfig(String host, int port) {
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 8080;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    public ServerConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host não pode ser vazio");
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("Porta inválida: " + port + ". Deve estar entre " + MIN_PORT + " e " + MAX_PORT + ".");
        }
    }

    public static ServerConfig fromEnv(String[] args) {
        String host = Config.get("HOST", DEFAULT_HOST);
        int port = (args != null && args.length > 0 && args[0] != null && !args[0].isBlank())
                ? Integer.parseInt(args[0].trim())
                : Config.getInt("PORT", DEFAULT_PORT);
        return new ServerConfig(host, port);
    }
}
