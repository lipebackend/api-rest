package com.taskmanagement.api.infra.web.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanagement.api.domain.contract.repository.TaskRepository;
import com.taskmanagement.api.domain.contract.services.TaskService;
import com.taskmanagement.api.domain.service.TaskServiceImpl;
import com.taskmanagement.api.infra.repository.InMemoryTaskRepository;
import com.taskmanagement.api.infra.web.http.handler.TaskHandler;
import com.taskmanagement.api.infra.web.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TaskHandlerIntegrationTest {

        private static final Duration TIMEOUT = Duration.ofSeconds(5);

        private Server server;
        private HttpClient client;
        private ObjectMapper objectMapper;
        private String baseUrl;

        @BeforeEach
        void setUp() throws IOException {
                TaskRepository repository = new InMemoryTaskRepository();
                TaskService service = new TaskServiceImpl(repository);
                objectMapper = JsonMapper.create();
                TaskHandler handler = new TaskHandler(service, objectMapper);

                server = new Server("localhost", 0);
                server.createContext("/tasks", handler);
                server.createContext("/health", new com.taskmanagement.api.infra.web.http.handler.HealthHandler());
                server.start();

                int port = server.getPort();
                baseUrl = "http://localhost:" + port + "/tasks";
                client = HttpClient.newBuilder()
                                .connectTimeout(TIMEOUT)
                                .build();
        }

        @AfterEach
        void tearDown() {
                if (server != null) {
                        server.stop(0);
                }
        }

        @Test
        @DisplayName("GET /tasks should return empty list initially")
        void shouldReturnEmptyList() throws Exception {
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl))
                                .timeout(TIMEOUT)
                                .GET()
                                .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().firstValue("Content-Type")).contains("application/json; charset=UTF-8");
                assertThat(response.body()).isEqualTo("[]");
        }

        @Test
        @DisplayName("POST /tasks should create task and return 201 Created with JSON")
        void shouldCreateTaskSuccessfully() throws Exception {
                String jsonBody = "{\"title\": \"Aprender Java Moderno\"}";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl))
                                .header("Content-Type", "application/json")
                                .timeout(TIMEOUT)
                                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                                .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.headers().firstValue("Content-Type")).contains("application/json; charset=UTF-8");

                JsonNode root = objectMapper.readTree(response.body());
                assertThat(root.get("id").asText()).isNotBlank();
                assertThat(root.get("title").asText()).isEqualTo("Aprender Java Moderno");
                assertThat(root.get("completed").asBoolean()).isFalse();
                assertThat(root.get("createdAt").asText()).isNotBlank();
        }

        @Test
        @DisplayName("POST /tasks with missing title should return 400 Bad Request")
        void shouldReturn400WhenTitleMissing() throws Exception {
                String jsonBody = "{}";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl))
                                .header("Content-Type", "application/json")
                                .timeout(TIMEOUT)
                                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                                .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(400);
                JsonNode root = objectMapper.readTree(response.body());
                assertThat(root.get("error").asText()).contains("Field 'title' is required and cannot be blank");
        }

        @Test
        @DisplayName("POST /tasks with malformed JSON should return 400 Bad Request")
        void shouldReturn400WhenJsonMalformed() throws Exception {
                String jsonBody = "{ invalid json ";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl))
                                .header("Content-Type", "application/json")
                                .timeout(TIMEOUT)
                                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                                .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(400);
                JsonNode root = objectMapper.readTree(response.body());
                assertThat(root.has("error")).isTrue();
        }

        @Test
        @DisplayName("POST /tasks without Content-Type application/json should return 415")
        void shouldReturn415WhenContentTypeInvalid() throws Exception {
                String jsonBody = "{\"title\": \"Teste\"}";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl))
                                .header("Content-Type", "text/plain")
                                .timeout(TIMEOUT)
                                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                                .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(415);
                JsonNode root = objectMapper.readTree(response.body());
                assertThat(root.get("error").asText()).contains("Content-Type must be application/json");
        }

        @Test
        @DisplayName("POST /tasks with payload > 64KB should return 413 Payload Too Large")
        void shouldReturn413WhenPayloadTooLarge() throws Exception {
                String hugeTitle = "A".repeat(70 * 1024);
                String jsonBody = "{\"title\": \"" + hugeTitle + "\"}";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl))
                                .header("Content-Type", "application/json")
                                .timeout(TIMEOUT)
                                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                                .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(413);
                JsonNode root = objectMapper.readTree(response.body());
                assertThat(root.get("error").asText()).contains("exceeds maximum allowed size");
        }

        @Test
        @DisplayName("GET /tasks/{id} should return 200 or 404")
        void shouldGetByIdOrReturn404() throws Exception {
                // Create task
                String id = createTask("Tarefa Para Consulta");

                // Found
                HttpRequest getReq = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/" + id))
                                .timeout(TIMEOUT)
                                .GET()
                                .build();
                HttpResponse<String> getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString());
                assertThat(getRes.statusCode()).isEqualTo(200);
                JsonNode root = objectMapper.readTree(getRes.body());
                assertThat(root.get("id").asText()).isEqualTo(id);
                assertThat(root.get("title").asText()).isEqualTo("Tarefa Para Consulta");

                // Not Found
                HttpRequest notFoundReq = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/id-inexistente"))
                                .timeout(TIMEOUT)
                                .GET()
                                .build();
                HttpResponse<String> notFoundRes = client.send(notFoundReq, HttpResponse.BodyHandlers.ofString());
                assertThat(notFoundRes.statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("PUT /tasks/{id} should enforce full replacement (title and completed)")
        void shouldEnforcePutFullReplacement() throws Exception {
                String id = createTask("Tarefa Inicial");

                // Missing completed in PUT
                HttpRequest putMissingCompleted = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/" + id))
                                .header("Content-Type", "application/json")
                                .timeout(TIMEOUT)
                                .PUT(HttpRequest.BodyPublishers.ofString("{\"title\": \"Novo Título\"}"))
                                .build();
                HttpResponse<String> missingRes = client.send(putMissingCompleted,
                                HttpResponse.BodyHandlers.ofString());
                assertThat(missingRes.statusCode()).isEqualTo(400);

                // Valid PUT
                HttpRequest validPut = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/" + id))
                                .header("Content-Type", "application/json")
                                .timeout(TIMEOUT)
                                .PUT(HttpRequest.BodyPublishers
                                                .ofString("{\"title\": \"Título Substituído\", \"completed\": true}"))
                                .build();
                HttpResponse<String> validRes = client.send(validPut, HttpResponse.BodyHandlers.ofString());
                assertThat(validRes.statusCode()).isEqualTo(200);

                JsonNode root = objectMapper.readTree(validRes.body());
                assertThat(root.get("title").asText()).isEqualTo("Título Substituído");
                assertThat(root.get("completed").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("PATCH /tasks/{id} should support partial update of title or completed")
        void shouldSupportPatchPartialUpdate() throws Exception {
                String id = createTask("Tarefa Inicial");

                // Patch completed only
                HttpRequest patchCompleted = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/" + id))
                                .header("Content-Type", "application/json")
                                .timeout(TIMEOUT)
                                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"completed\": true}"))
                                .build();
                HttpResponse<String> res1 = client.send(patchCompleted, HttpResponse.BodyHandlers.ofString());
                assertThat(res1.statusCode()).isEqualTo(200);
                JsonNode root1 = objectMapper.readTree(res1.body());
                assertThat(root1.get("title").asText()).isEqualTo("Tarefa Inicial");
                assertThat(root1.get("completed").asBoolean()).isTrue();

                // Patch title only
                HttpRequest patchTitle = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/" + id))
                                .header("Content-Type", "application/json")
                                .timeout(TIMEOUT)
                                .method("PATCH", HttpRequest.BodyPublishers
                                                .ofString("{\"title\": \"Título Modificado\"}"))
                                .build();
                HttpResponse<String> res2 = client.send(patchTitle, HttpResponse.BodyHandlers.ofString());
                assertThat(res2.statusCode()).isEqualTo(200);
                JsonNode root2 = objectMapper.readTree(res2.body());
                assertThat(root2.get("title").asText()).isEqualTo("Título Modificado");
                assertThat(root2.get("completed").asBoolean()).isTrue();

                // Empty PATCH body should fail with 400
                HttpRequest emptyPatch = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/" + id))
                                .header("Content-Type", "application/json")
                                .timeout(TIMEOUT)
                                .method("PATCH", HttpRequest.BodyPublishers.ofString("{}"))
                                .build();
                HttpResponse<String> resEmpty = client.send(emptyPatch, HttpResponse.BodyHandlers.ofString());
                assertThat(resEmpty.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("DELETE /tasks/{id} should return 204 No Content with no body and no Content-Type")
        void shouldReturn204OnDelete() throws Exception {
                String id = createTask("Tarefa para deletar");

                HttpRequest deleteReq = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/" + id))
                                .timeout(TIMEOUT)
                                .DELETE()
                                .build();
                HttpResponse<String> deleteRes = client.send(deleteReq, HttpResponse.BodyHandlers.ofString());

                assertThat(deleteRes.statusCode()).isEqualTo(204);
                assertThat(deleteRes.body()).isEmpty();
                assertThat(deleteRes.headers().firstValue("Content-Type")).isEmpty();

                // Subsequent GET should return 404
                HttpRequest getReq = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/" + id))
                                .timeout(TIMEOUT)
                                .GET()
                                .build();
                HttpResponse<String> getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString());
                assertThat(getRes.statusCode()).isEqualTo(404);

                // Subsequent DELETE should return 404
                HttpResponse<String> secondDeleteRes = client.send(deleteReq, HttpResponse.BodyHandlers.ofString());
                assertThat(secondDeleteRes.statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("GET /tasks with ?completed query parameter should filter properly")
        void shouldFilterTasksByQuery() throws Exception {
                String id1 = createTask("Tarefa 1");
                String id2 = createTask("Tarefa 2");

                // Mark id2 completed
                HttpRequest putReq = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/" + id2))
                                .header("Content-Type", "application/json")
                                .timeout(TIMEOUT)
                                .PUT(HttpRequest.BodyPublishers
                                                .ofString("{\"title\": \"Tarefa 2\", \"completed\": true}"))
                                .build();
                client.send(putReq, HttpResponse.BodyHandlers.ofString());

                // Filter completed=true
                HttpRequest getCompleted = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "?completed=true"))
                                .timeout(TIMEOUT)
                                .GET()
                                .build();
                HttpResponse<String> resCompleted = client.send(getCompleted, HttpResponse.BodyHandlers.ofString());
                assertThat(resCompleted.statusCode()).isEqualTo(200);
                JsonNode jsonCompleted = objectMapper.readTree(resCompleted.body());
                assertThat(jsonCompleted).hasSize(1);
                assertThat(jsonCompleted.get(0).get("id").asText()).isEqualTo(id2);

                // Filter completed=false
                HttpRequest getPending = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "?completed=false"))
                                .timeout(TIMEOUT)
                                .GET()
                                .build();
                HttpResponse<String> resPending = client.send(getPending, HttpResponse.BodyHandlers.ofString());
                assertThat(resPending.statusCode()).isEqualTo(200);
                JsonNode jsonPending = objectMapper.readTree(resPending.body());
                assertThat(jsonPending).hasSize(1);
                assertThat(jsonPending.get(0).get("id").asText()).isEqualTo(id1);

                // Invalid filter value
                HttpRequest getInvalid = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "?completed=notabool"))
                                .timeout(TIMEOUT)
                                .GET()
                                .build();
                HttpResponse<String> resInvalid = client.send(getInvalid, HttpResponse.BodyHandlers.ofString());
                assertThat(resInvalid.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("Method not allowed should return 405 with Allow header")
        void shouldReturn405WhenMethodNotAllowed() throws Exception {
                HttpRequest deleteList = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl))
                                .timeout(TIMEOUT)
                                .DELETE()
                                .build();
                HttpResponse<String> res = client.send(deleteList, HttpResponse.BodyHandlers.ofString());

                assertThat(res.statusCode()).isEqualTo(405);
                assertThat(res.headers().firstValue("Allow")).contains("GET, POST");
        }

        @Test
        @DisplayName("GET /health/live and /health/ready should return 200 UP")
        void shouldReturn200ForHealthProbes() throws Exception {
                String healthBase = baseUrl.replace("/tasks", "/health");

                HttpRequest liveReq = HttpRequest.newBuilder()
                                .uri(URI.create(healthBase + "/live"))
                                .timeout(TIMEOUT)
                                .GET()
                                .build();
                HttpResponse<String> liveRes = client.send(liveReq, HttpResponse.BodyHandlers.ofString());
                assertThat(liveRes.statusCode()).isEqualTo(200);
                assertThat(liveRes.body()).contains("\"status\":\"UP\"");

                HttpRequest readyReq = HttpRequest.newBuilder()
                                .uri(URI.create(healthBase + "/ready"))
                                .timeout(TIMEOUT)
                                .GET()
                                .build();
                HttpResponse<String> readyRes = client.send(readyReq, HttpResponse.BodyHandlers.ofString());
                assertThat(readyRes.statusCode()).isEqualTo(200);
                assertThat(readyRes.body()).contains("\"status\":\"UP\"");
        }

        private String createTask(String title) throws Exception {
                String json = "{\"title\": \"" + title + "\"}";
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl))
                                .header("Content-Type", "application/json")
                                .timeout(TIMEOUT)
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(201);
                JsonNode root = objectMapper.readTree(response.body());
                String id = root.get("id").asText();
                assertThat(response.headers().firstValue("Location")).contains("/tasks/" + id);
                return id;
        }
}
