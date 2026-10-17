package com.taskmanagement.api.infra.web.http.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.taskmanagement.api.domain.contract.services.TaskService;
import com.taskmanagement.api.domain.exception.TaskNotFoundException;
import com.taskmanagement.api.domain.exception.ValidationException;
import com.taskmanagement.api.domain.model.Task;
import com.taskmanagement.api.infra.repository.RepositoryException;
import com.taskmanagement.api.infra.web.dto.CreateTaskRequest;
import com.taskmanagement.api.infra.web.dto.ErrorResponse;
import com.taskmanagement.api.infra.web.dto.PatchTaskRequest;
import com.taskmanagement.api.infra.web.dto.TaskResponse;
import com.taskmanagement.api.infra.web.dto.UpdateTaskRequest;
import com.taskmanagement.api.infra.web.http.exception.GenericException;
import com.taskmanagement.api.infra.web.http.exception.PayloadTooLargeException;
import com.taskmanagement.api.infra.web.http.exception.UnsupportedMediaTypeException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TaskHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(TaskHandler.class.getName());
    private static final int MAX_BODY_SIZE_BYTES = 64 * 1024; // 64 KB
    private static final String JSON_MEDIA_TYPE = "application/json";

    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public TaskHandler(TaskService taskService, ObjectMapper objectMapper) {
        this.taskService = Objects.requireNonNull(taskService, "taskService cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod().toUpperCase();
            String rawPath = exchange.getRequestURI().getRawPath();
            String normalizedPath = trimSlashes(rawPath != null ? rawPath : exchange.getRequestURI().getPath());
            String[] segments = normalizedPath.isEmpty() ? new String[0] : normalizedPath.split("/");

            LOGGER.log(Level.INFO, "Incoming request: {0} {1}", new Object[] { method, rawPath });
            if (segments.length == 0 || !segments[0].equals("tasks")) {
                sendError(exchange, 404, "Endpoint not found");
                return;
            }

            if (segments.length == 1) {
                switch (method) {
                    case "GET" -> handleGetAll(exchange);
                    case "POST" -> handleCreate(exchange);
                    default -> {
                        exchange.getResponseHeaders().set("Allow", "GET, POST");
                        sendError(exchange, 405, "Method " + method + " not allowed on /tasks");
                    }
                }
            } else if (segments.length == 2) {
                String taskId = decodeUrlComponent(segments[1]);
                switch (method) {
                    case "GET" -> handleGetById(exchange, taskId);
                    case "PUT" -> handleUpdate(exchange, taskId);
                    case "PATCH" -> handlePatch(exchange, taskId);
                    case "DELETE" -> handleDelete(exchange, taskId);
                    default -> {
                        exchange.getResponseHeaders().set("Allow", "GET, PUT, PATCH, DELETE");
                        sendError(exchange, 405, "Method " + method + " not allowed on /tasks/{id}");
                    }
                }
            } else {
                sendError(exchange, 404, "Resource not found");
            }
        } catch (GenericException e) {
            sendError(exchange, e.getStatusCode(), e.getMessage());
        } catch (ValidationException e) {
            sendError(exchange, 400, "Validation error: " + e.getMessage());
        } catch (TaskNotFoundException e) {
            sendError(exchange, 404, e.getMessage());
        } catch (RepositoryException e) {
            LOGGER.log(Level.SEVERE, "Database unavailable while handling request", e);
            sendError(exchange, 503, "Service temporarily unavailable");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error while handling request", e);
            sendError(exchange, 500, "Internal server error");
        } finally {
            exchange.close();
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        LOGGER.log(Level.INFO, "Handling GET /tasks request");
        String rawQuery = exchange.getRequestURI().getRawQuery();
        Boolean completedFilter = parseCompletedQuery(rawQuery);

        List<Task> tasks = taskService.getAllTasks(completedFilter);
        List<TaskResponse> responses = tasks.stream()
                .map(TaskResponse::from)
                .toList();

        sendJsonResponse(exchange, 200, responses);
    }

    private void handleGetById(HttpExchange exchange, String taskId) throws IOException {
        LOGGER.log(Level.INFO, "Handling GET /tasks/{id} request");
        Task task = taskService.getTaskById(taskId);
        sendJsonResponse(exchange, 200, TaskResponse.from(task));
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        LOGGER.log(Level.INFO, "Handling POST /tasks request");
        validateContentType(exchange);
        String body = readRequestBody(exchange);

        CreateTaskRequest request = parseRequestBody(body, CreateTaskRequest.class);
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new ValidationException("Field 'title' is required and cannot be blank");
        }

        Task created = taskService.createTask(request.title());
        exchange.getResponseHeaders().set("Location", "/tasks/" + created.getId());
        sendJsonResponse(exchange, 201, TaskResponse.from(created));
    }

    private void handleUpdate(HttpExchange exchange, String taskId) throws IOException {
        LOGGER.log(Level.INFO, "Handling PUT /tasks/{id} request");
        validateContentType(exchange);
        String body = readRequestBody(exchange);

        UpdateTaskRequest request = parseRequestBody(body, UpdateTaskRequest.class);
        if (request == null) {
            throw new ValidationException("Request body cannot be empty");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new ValidationException("Field 'title' is required and cannot be blank");
        }
        if (request.completed() == null) {
            throw new ValidationException("Field 'completed' is required for PUT");
        }

        Task updated = taskService.replaceTask(taskId, request.title(), request.completed());
        sendJsonResponse(exchange, 200, TaskResponse.from(updated));
    }

    private void handlePatch(HttpExchange exchange, String taskId) throws IOException {
        LOGGER.log(Level.INFO, "Handling PATCH /tasks/{id} request");
        validateContentType(exchange);
        String body = readRequestBody(exchange);

        PatchTaskRequest request = parseRequestBody(body, PatchTaskRequest.class);
        if (request == null || (request.title() == null && request.completed() == null)) {
            throw new ValidationException("Request body must contain 'title' and/or 'completed'");
        }
        if (request.title() != null && request.title().isBlank()) {
            throw new ValidationException("Field 'title' cannot be blank");
        }

        Task patched = taskService.patchTask(taskId, request.title(), request.completed());
        sendJsonResponse(exchange, 200, TaskResponse.from(patched));
    }

    private void handleDelete(HttpExchange exchange, String taskId) throws IOException {
        LOGGER.log(Level.INFO, "Handling DELETE /tasks/{id} request");
        taskService.deleteTask(taskId);
        sendNoContent(exchange);
    }

    private <T> T parseRequestBody(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Invalid JSON: " + e.getOriginalMessage());
        }
    }

    private void validateContentType(HttpExchange exchange) {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith(JSON_MEDIA_TYPE)) {
            throw new UnsupportedMediaTypeException("Content-Type must be " + JSON_MEDIA_TYPE);
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int totalBytes = 0;
            int bytesRead;

            while ((bytesRead = is.read(chunk)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > MAX_BODY_SIZE_BYTES) {
                    throw new PayloadTooLargeException(
                            "Request body exceeds maximum allowed size of " + MAX_BODY_SIZE_BYTES + " bytes");
                }
                buffer.write(chunk, 0, bytesRead);
            }

            String content = buffer.toString(StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                throw new ValidationException("Request body cannot be empty");
            }
            return content;
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] responseBytes = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendNoContent(HttpExchange exchange) throws IOException {
        // RFC 9110: 204 No Content MUST NOT include representation data or Content-Type header
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String errorMessage = message == null ? "Unknown error" : message;
        ErrorResponse errorResponse = new ErrorResponse(errorMessage);
        sendJsonResponse(exchange, statusCode, errorResponse);
    }

    private Boolean parseCompletedQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        for (String param : rawQuery.split("&")) {
            String[] pair = param.split("=", 2);
            String key = decodeUrlComponent(pair[0]);
            if (pair.length == 2 && "completed".equalsIgnoreCase(key)) {
                String value = decodeUrlComponent(pair[1]).trim();
                if ("true".equalsIgnoreCase(value)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(value)) {
                    return false;
                }
                throw new ValidationException("Query parameter 'completed' must be 'true' or 'false'");
            }
        }

        return null;
    }

    private static String decodeUrlComponent(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid URL encoding in request URI");
        }
    }

    private static String trimSlashes(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(start, end);
    }
}
