package com.taskmanagement.api.domain.service;

import com.taskmanagement.api.domain.contract.cache.TaskCache;
import com.taskmanagement.api.domain.contract.queue.TaskQueuePublisher;
import com.taskmanagement.api.domain.contract.repository.TaskRepository;
import com.taskmanagement.api.domain.contract.services.TaskService;
import com.taskmanagement.api.domain.exception.TaskNotFoundException;
import com.taskmanagement.api.domain.exception.ValidationException;
import com.taskmanagement.api.domain.model.Task;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TaskServiceImpl implements TaskService {

    private static final TaskCache NO_OP_CACHE = new TaskCache() {
        @Override
        public Optional<Task> get(String id) {
            return Optional.empty();
        }

        @Override
        public void put(Task task) {
        }

        @Override
        public void evict(String id) {
        }
    };

    private static final TaskQueuePublisher NO_OP_PUBLISHER = new TaskQueuePublisher() {
        @Override
        public void publishTaskCreatedEvent(Task task) {
        }

        @Override
        public void publishTaskUpdatedEvent(Task task) {
        }

        @Override
        public void publishTaskDeletedEvent(String taskId) {
        }
    };

    private final TaskRepository taskRepository;
    private final TaskCache taskCache;
    private final TaskQueuePublisher taskQueuePublisher;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this(taskRepository, NO_OP_CACHE, NO_OP_PUBLISHER);
    }

    public TaskServiceImpl(TaskRepository taskRepository, TaskCache taskCache) {
        this(taskRepository, taskCache, NO_OP_PUBLISHER);
    }

    public TaskServiceImpl(
            TaskRepository taskRepository,
            TaskCache taskCache,
            TaskQueuePublisher taskQueuePublisher) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository cannot be null");
        this.taskCache = Objects.requireNonNull(taskCache, "taskCache cannot be null");
        this.taskQueuePublisher = Objects.requireNonNull(taskQueuePublisher, "taskQueuePublisher cannot be null");
    }

    @Override
    public List<Task> getAllTasks(Boolean completedFilter) {
        return taskRepository.findAll(completedFilter);
    }

    @Override
    public Task getTaskById(String id) {
        String validatedId = validateId(id);
        // Padrão Cache-Aside: tenta ler do cache; em caso de miss, busca no repositório e popula o cache
        return taskCache.get(validatedId)
                .orElseGet(() -> {
                    Task task = taskRepository.findById(validatedId)
                            .orElseThrow(() -> new TaskNotFoundException(validatedId));
                    taskCache.put(task);
                    return task;
                });
    }

    @Override
    public Task createTask(String title) {
        String validatedTitle = validateTitle(title);
        Task task = Task.create(validatedTitle);
        Task saved = taskRepository.save(task);
        taskCache.evict(saved.getId());
        taskQueuePublisher.publishTaskCreatedEvent(saved);
        return saved;
    }

    @Override
    public Task replaceTask(String id, String title, boolean completed) {
        String validatedId = validateId(id);
        String validatedTitle = validateTitle(title);

        Task updated = taskRepository.update(
                        validatedId,
                        existing -> existing.withTitleAndCompleted(validatedTitle, completed))
                .orElseThrow(() -> new TaskNotFoundException(validatedId));

        // Invalida o cache após escrita confirmada no banco para evitar inconsistência (dual-write)
        taskCache.evict(validatedId);
        taskQueuePublisher.publishTaskUpdatedEvent(updated);
        return updated;
    }

    @Override
    public Task patchTask(String id, String title, Boolean completed) {
        String validatedId = validateId(id);

        if (title == null && completed == null) {
            throw new ValidationException("Request body must contain 'title' and/or 'completed'");
        }

        final String validatedTitle = (title != null) ? validateTitle(title) : null;

        Task updated = taskRepository.update(validatedId, existing -> {
            Task t = existing;
            if (validatedTitle != null) {
                t = t.withTitle(validatedTitle);
            }
            if (completed != null) {
                t = t.withCompleted(completed);
            }
            return t;
        }).orElseThrow(() -> new TaskNotFoundException(validatedId));

        taskCache.evict(validatedId);
        taskQueuePublisher.publishTaskUpdatedEvent(updated);
        return updated;
    }

    @Override
    public void deleteTask(String id) {
        String validatedId = validateId(id);
        boolean deleted = taskRepository.deleteById(validatedId);
        if (!deleted) {
            throw new TaskNotFoundException(validatedId);
        }
        taskCache.evict(validatedId);
        taskQueuePublisher.publishTaskDeletedEvent(validatedId);
    }

    private static String validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Task ID cannot be null or blank");
        }
        return id.strip();
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("Field 'title' is required and cannot be blank");
        }
        String stripped = title.strip();
        if (stripped.length() > Task.MAX_TITLE_LENGTH) {
            throw new ValidationException(
                    "Field 'title' cannot exceed " + Task.MAX_TITLE_LENGTH + " characters");
        }
        return stripped;
    }
}
