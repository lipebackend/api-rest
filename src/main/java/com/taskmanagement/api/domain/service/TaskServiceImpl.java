package com.taskmanagement.api.domain.service;

import com.taskmanagement.api.domain.contract.cache.TaskCache;
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

    private final TaskRepository taskRepository;
    private final TaskCache taskCache;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this(taskRepository, NO_OP_CACHE);
    }

    public TaskServiceImpl(TaskRepository taskRepository, TaskCache taskCache) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository cannot be null");
        this.taskCache = Objects.requireNonNull(taskCache, "taskCache cannot be null");
    }

    @Override
    public List<Task> getAllTasks(Boolean completedFilter) {
        return taskRepository.findAll(completedFilter);
    }

    @Override
    public Task getTaskById(String id) {
        String validatedId = validateId(id);
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
        taskRepository.save(task);
        taskCache.put(task);
        return task;
    }

    @Override
    public Task replaceTask(String id, String title, boolean completed) {
        String validatedId = validateId(id);
        String validatedTitle = validateTitle(title);

        Task task = taskRepository.update(validatedId, existing -> existing.withTitleAndCompleted(validatedTitle, completed))
                .orElseThrow(() -> new TaskNotFoundException(validatedId));
        taskCache.put(task);
        return task;
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

        taskCache.put(updated);
        return updated;
    }

    @Override
    public void deleteTask(String id) {
        String validatedId = validateId(id);
        boolean deleted = taskRepository.deleteById(validatedId);
        taskCache.evict(validatedId);
        if (!deleted) {
            throw new TaskNotFoundException(validatedId);
        }
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
        return title.strip();
    }
}
