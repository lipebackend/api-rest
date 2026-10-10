package com.taskmanagement.api.infra.web.http.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler HTTP para probes de saúde do Kubernetes e Podman:
 * <ul>
 *   <li>{@code GET /health/live}: Liveness Probe — indica se o processo JVM e o servidor HTTP estão vivos.</li>
 *   <li>{@code GET /health/ready}: Readiness Probe — valida se o banco de dados (quando ativo) aceita conexões.</li>
 * </ul>
 */
public class HealthHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HealthHandler.class.getName());
    private static final byte[] UP_JSON = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DOWN_JSON = "{\"status\":\"DOWN\"}".getBytes(StandardCharsets.UTF_8);

    private final DataSource dataSource;

    public HealthHandler() {
        this(null);
    }

    public HealthHandler(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/live") || path.equals("/health") || path.equals("/health/")) {
                sendJson(exchange, 200, UP_JSON);
            } else if (path.endsWith("/ready")) {
                if (isDatabaseReady()) {
                    sendJson(exchange, 200, UP_JSON);
                } else {
                    sendJson(exchange, 503, DOWN_JSON);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } finally {
            exchange.close();
        }
    }

    private boolean isDatabaseReady() {
        if (dataSource == null) {
            return true;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Readiness check falhou ao validar conexão com o banco", exception);
            return false;
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, byte[] payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }
}
