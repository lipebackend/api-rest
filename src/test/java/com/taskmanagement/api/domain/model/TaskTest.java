package com.taskmanagement.api.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTest {

    @Test
    @DisplayName("Should create task via factory method with generated ID, false status, and timestamp")
    void shouldCreateTaskWithFactory() {
        Task task = Task.create("Estudar Concorrência em Java");

        assertThat(task.getId()).isNotBlank();
        assertThat(task.getTitle()).isEqualTo("Estudar Concorrência em Java");
        assertThat(task.isCompleted()).isFalse();
        assertThat(task.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when title is null or blank")
    void shouldThrowWhenTitleIsInvalid() {
        assertThatThrownBy(() -> Task.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Title cannot be null or blank");

        assertThatThrownBy(() -> Task.create("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Title cannot be null or blank");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when ID is null or blank")
    void shouldThrowWhenIdIsInvalid() {
        assertThatThrownBy(() -> new Task(null, "Title", false, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task ID cannot be null or blank");

        assertThatThrownBy(() -> new Task("  ", "Title", false, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task ID cannot be null or blank");
    }

    @Test
    @DisplayName("Should preserve immutability when using wither methods")
    void shouldPreserveImmutabilityWithWitherMethods() {
        Task original = Task.create("Título Original");
        Task updatedTitle = original.withTitle("Novo Título");
        Task updatedCompleted = original.withCompleted(true);
        Task updatedBoth = original.withTitleAndCompleted("Título Final", true);

        // Original unchanged
        assertThat(original.getTitle()).isEqualTo("Título Original");
        assertThat(original.isCompleted()).isFalse();

        // New instances have expected values and same ID
        assertThat(updatedTitle.getTitle()).isEqualTo("Novo Título");
        assertThat(updatedTitle.isCompleted()).isFalse();
        assertThat(updatedTitle.getId()).isEqualTo(original.getId());

        assertThat(updatedCompleted.getTitle()).isEqualTo("Título Original");
        assertThat(updatedCompleted.isCompleted()).isTrue();

        assertThat(updatedBoth.getTitle()).isEqualTo("Título Final");
        assertThat(updatedBoth.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("Should evaluate equality based on task ID")
    void shouldEvaluateEqualityBasedOnId() {
        Instant now = Instant.now();
        Task t1 = new Task("id-123", "Tarefa 1", false, now);
        Task t2 = new Task("id-123", "Tarefa 2 Alterada", true, now);
        Task t3 = new Task("id-456", "Tarefa 1", false, now);

        assertThat(t1).isEqualTo(t2);
        assertThat(t1.hashCode()).isEqualTo(t2.hashCode());
        assertThat(t1).isNotEqualTo(t3);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when title exceeds 200 characters")
    void shouldThrowWhenTitleExceedsMaxLength() {
        String validMaxTitle = "a".repeat(Task.MAX_TITLE_LENGTH);
        Task validTask = Task.create(validMaxTitle);
        assertThat(validTask.getTitle()).hasSize(Task.MAX_TITLE_LENGTH);

        String oversizedTitle = "a".repeat(Task.MAX_TITLE_LENGTH + 1);
        assertThatThrownBy(() -> Task.create(oversizedTitle))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Title cannot exceed 200 characters");
    }
}
