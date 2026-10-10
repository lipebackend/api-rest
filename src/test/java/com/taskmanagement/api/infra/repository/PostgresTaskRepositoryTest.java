package com.taskmanagement.api.infra.repository;

import com.taskmanagement.api.domain.model.Task;
import com.taskmanagement.api.infra.db.DatabaseConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresTaskRepositoryTest {

    private HikariDataSource dataSource;
    private PostgresTaskRepository repository;

    @BeforeEach
    void setUp() {
        String jdbcUrl = "jdbc:h2:mem:tasks_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        dataSource = DatabaseConfig.createDataSource(jdbcUrl, "sa", "");
        DatabaseConfig.runMigrations(dataSource);
        repository = new PostgresTaskRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("Should guarantee Read-Your-Own-Writes immediately after synchronous save")
    void shouldSaveAndImmediatelyFindById() {
        Task task = Task.create("Persistência síncrona no banco");
        repository.save(task);

        Optional<Task> found = repository.findById(task.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(task.getId());
        assertThat(found.get().getTitle()).isEqualTo("Persistência síncrona no banco");
        assertThat(found.get().isCompleted()).isFalse();
        assertThat(repository.existsById(task.getId())).isTrue();
    }

    @Test
    @DisplayName("Should filter tasks by completed status and respect ordering")
    void shouldFilterTasksByCompletedStatus() {
        Task t1 = Task.create("Pendente 1");
        Task t2 = Task.create("Concluída 1").withCompleted(true);
        Task t3 = Task.create("Pendente 2");

        repository.save(t1);
        repository.save(t2);
        repository.save(t3);

        assertThat(repository.findAll(null)).hasSize(3);
        assertThat(repository.findAll(true)).extracting(Task::getId).containsExactly(t2.getId());
        assertThat(repository.findAll(false)).extracting(Task::getId).containsExactlyInAnyOrder(t1.getId(), t3.getId());
    }

    @Test
    @DisplayName("Should prevent lost updates when 10 Virtual Threads concurrently toggle completed with SELECT FOR UPDATE")
    void shouldPreventLostUpdatesAcrossConcurrentVirtualThreads() throws Exception {
        Task initial = Task.create("Contador concorrente");
        repository.save(initial);

        int threads = 10;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await();
                        repository.update(initial.getId(), existing -> existing.withCompleted(!existing.isCompleted()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        }

        // 10 inversões atômicas a partir de false devem terminar exatamente em false (par)
        Optional<Task> finalState = repository.findById(initial.getId());
        assertThat(finalState).isPresent();
        assertThat(finalState.get().isCompleted()).isFalse();
    }

    @Test
    @DisplayName("Should delete task by ID and return false when deleting nonexistent task")
    void shouldDeleteByIdCorrectly() {
        Task task = Task.create("Para remover");
        repository.save(task);

        assertThat(repository.deleteById(task.getId())).isTrue();
        assertThat(repository.findById(task.getId())).isEmpty();
        assertThat(repository.existsById(task.getId())).isFalse();
        assertThat(repository.deleteById(task.getId())).isFalse();
    }

    @Test
    @DisplayName("Should wrap SQL failures in RepositoryException")
    void shouldWrapSqlFailuresInRepositoryException() {
        dataSource.close();
        assertThatThrownBy(() -> repository.findAll(null))
                .isInstanceOf(RepositoryException.class);
    }
}
