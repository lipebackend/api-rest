# Copilot instructions for this repository

## Project overview

This is a Java 21 task-management API built with the JDK's native `com.sun.net.httpserver.HttpServer` and Jackson. The codebase is intentionally organized around a small clean-architecture split:

- `src/main/java/com/taskmanagement/api/Main.java` bootstraps the HTTP server and wires the app together.
- `domain/` contains the core model and service contracts: `Task`, repository/service/cache/queue interfaces, and domain exceptions.
- `infra/` contains concrete implementations: HTTP handlers, JSON mapping, in-memory repository, Redis cache, RabbitMQ publisher, and server configuration.
- `src/test/java` contains unit and HTTP integration tests, with the main verification path being `mvn test` / `mvn verify`.

The default runtime wiring in `Main` uses `InMemoryTaskRepository`, so tests and local runs are easy to execute without external infrastructure. The repository also contains optional infrastructure-specific adapters (Postgres, Redis, RabbitMQ) and the app is designed to be extended via constructor injection rather than static singletons.

## Build, test, and validation commands

Use Maven as the source of truth.

- Full verification in CI:
  - `mvn -B -ntp verify`
- Run the test suite locally:
  - `mvn test`
- Run a single test class:
  - `mvn -Dtest=com.taskmanagement.api.infra.web.http.TaskHandlerIntegrationTest test`
- Run a single test method:
  - `mvn -Dtest=TaskServiceImplTest#shouldCreateTask test`
- Compile only:
  - `mvn -q -DskipTests compile`

There is no dedicated ESLint/SpotBugs/Checkstyle config in this repo. The project relies on Java compilation plus Maven tests and CI workflow validation. The GitHub Actions workflow runs Java 25 in CI, while the project is configured for Java 21 release (`maven.compiler.release=21`).

## High-level architecture

### HTTP layer

`infra/web/http/handler/TaskHandler` is the primary request entrypoint. It:

- normalizes the URI path and routes `/tasks` and `/tasks/{id}`
- enforces `Allow` headers for unsupported methods
- validates `Content-Type: application/json` for write operations
- limits request bodies to 64 KB
- deserializes JSON DTOs into records and translates validation errors into structured JSON responses
- sends consistent HTTP status codes (`200`, `201`, `204`, `400`, `404`, `405`, `413`, `415`, `500`)

Most API behavior is expressed here, so changes to request/response semantics should start in this class.

### Domain layer

`Task` is the core immutable domain model. It owns validation for `id` and `title`, exposes `withTitle`, `withCompleted`, and `withTitleAndCompleted`, and is intentionally not mutable. Domain services are implemented in `TaskServiceImpl` and are responsible for validation plus repository/cache coordination.

### Persistence and cache

The repository contract (`domain/contract/repository/TaskRepository`) is implemented by:

- `InMemoryTaskRepository` for local/test behavior
- `PostgresTaskRepository` for a JDBC/Postgres-backed version

The service layer also integrates with a cache abstraction (`TaskCache`), with a Redis-backed implementation available in `infra/cache/JedisTaskCache`. The repository/cache split is important: the service reads cache first and falls back to repository storage, then repopulates the cache.

### Optional infrastructure adapters

This repo includes queue and messaging support, but the default app wiring does not require them to run the task API locally. For example:

- `infra/queue/RabbitQueuePublisher` and `NoOpTaskQueuePublisher`
- `domain/contract/queue/TaskQueuePublisher`
- `RabbitConnectionFactory` / `RabbitConnectionBuilder`

Treat these as optional extension points rather than the core path for standard CRUD work.

## Project conventions to keep in mind

- Prefer constructor injection and immutable state. The project explicitly models domain value objects as immutable and avoids setters.
- Use record DTOs for HTTP payloads; see `CreateTaskRequest`, `UpdateTaskRequest`, `PatchTaskRequest`, `TaskResponse`, and `ErrorResponse`.
- Preserve the JSON contract: successful responses with a body use `Content-Type: application/json; charset=UTF-8`; `204 No Content` responses deliberately omit a body and content-type header.
- Validation belongs in the domain/service flow and should fail with structured `400` responses instead of blowing up with generic exceptions.
- Route behavior is defined by exact path segments and method checks in `TaskHandler`; do not introduce new path semantics without updating this routing flow.
- Most read/write operations are intentionally in-memory and thread-safe via `ConcurrentHashMap` / `ConcurrentMap`, which is the default design for this beginner/educational project.
- Keep changes aligned with the repository’s “clean layered” structure: HTTP input handling -> service logic -> repository/cache access -> domain model behavior.

## Repository-specific notes

- The app is a task API under `/tasks`; when adding or changing endpoints, preserve the same path conventions and status semantics.
- `Task.create(title)` assigns a UUID and sets `completed` to `false`.
- The `Task` model already includes a `getDescription()` stub that throws `UnsupportedOperationException`; avoid introducing new uses of this method unless the project explicitly addresses that gap.
- The README includes a long “roadmap” of architectural improvements (Postgres, Flyway, Redis, RabbitMQ, outbox, etc.). Treat those as future evolution notes, not the current default runtime behavior.

## When making changes

- Start from the behavior-sensitive layer: `TaskHandler` for HTTP contract changes, `TaskServiceImpl` for business validation, and `TaskRepository` implementations for persistence semantics.
- Keep new tests close to the behavior they cover; the repo already uses a mixture of unit tests and HTTP integration tests.
- If the change is infrastructure-related, prefer the existing abstractions (`TaskRepository`, `TaskCache`, `TaskQueuePublisher`) over directly coupling code to a concrete implementation.
