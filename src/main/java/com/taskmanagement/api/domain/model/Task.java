package com.taskmanagement.api.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Domain Entity representing a Task in the system.
 * Enforces identity, state validation invariants, and immutable transitions.
 */
public final class Task {
    public static final int MAX_TITLE_LENGTH = 200;

    private final String id;
    private final String title;
    private final boolean completed;
    private final Instant createdAt;

    public static Task create(String title) {
        return new Task(UUID.randomUUID().toString(), title, false, Instant.now());
    }

    public Task(String id, String title, boolean completed, Instant createdAt) {
        this.id = validateId(id);
        this.title = validateTitle(title);
        this.completed = completed;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    public Task(String id, String title) {
        this(id, title, false, Instant.now());
    }

    private static String validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be null or blank");
        }
        return id.strip();
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be null or blank");
        }
        String normalized = title.strip();
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "Title cannot exceed " + MAX_TITLE_LENGTH + " characters");
        }
        return normalized;
    }

    public Task withTitle(String newTitle) {
        return new Task(this.id, newTitle, this.completed, this.createdAt);
    }

    public Task withCompleted(boolean newCompleted) {
        return new Task(this.id, this.title, newCompleted, this.createdAt);
    }

    public Task withTitleAndCompleted(String newTitle, boolean newCompleted) {
        return new Task(this.id, newTitle, newCompleted, this.createdAt);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Task task = (Task) o;
        return Objects.equals(id, task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", completed=" + completed +
                ", createdAt=" + createdAt +
                '}';
    }
}
