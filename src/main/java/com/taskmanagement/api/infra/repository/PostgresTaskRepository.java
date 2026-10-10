package com.taskmanagement.api.infra.repository;

import com.taskmanagement.api.domain.contract.repository.TaskRepository;
import com.taskmanagement.api.domain.model.Task;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Implementação JDBC síncrona e ACID de {@link TaskRepository} para PostgreSQL.
 *
 * <p>Decisões técnicas:
 * <ul>
 *   <li>Sem DDL em runtime: a criação de tabelas fica a cargo do Flyway no startup.</li>
 *   <li>Escrita síncrona no {@link #save(Task)}: garante durabilidade (D do ACID) e Read-Your-Own-Writes.</li>
 *   <li>{@link OffsetDateTime} em UTC para colunas {@code TIMESTAMPTZ}, evitando desvios de fuso horário da JVM.</li>
 *   <li>Lock pessimista de linha ({@code SELECT ... FOR UPDATE}) em {@link #update} dentro da mesma transação
 *       para impedir lost updates sob concorrência (Virtual Threads / múltiplas réplicas).</li>
 * </ul>
 */
public class PostgresTaskRepository implements TaskRepository, AutoCloseable {

    private static final int DEFAULT_FIND_ALL_LIMIT = 100;

    private final DataSource dataSource;

    public PostgresTaskRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public List<Task> findAll(Boolean completedFilter) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, completed, created_at FROM tasks");
        if (completedFilter != null) {
            sql.append(" WHERE completed = ?");
        }
        sql.append(" ORDER BY created_at ASC LIMIT ?");

        List<Task> tasks = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            if (completedFilter != null) {
                stmt.setBoolean(paramIndex++, completedFilter);
            }
            stmt.setInt(paramIndex, DEFAULT_FIND_ALL_LIMIT);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tasks.add(mapRow(rs));
                }
            }
            return tasks;
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao listar tasks no PostgreSQL", e);
        }
    }

    @Override
    public Optional<Task> findById(String id) {
        String sql = "SELECT id, title, completed, created_at FROM tasks WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao buscar task por ID no PostgreSQL", e);
        }
    }

    @Override
    public Task save(Task task) {
        Objects.requireNonNull(task, "task cannot be null");
        String sql = "INSERT INTO tasks (id, title, completed, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, task.getId());
            stmt.setString(2, task.getTitle());
            stmt.setBoolean(3, task.isCompleted());
            stmt.setObject(4, task.getCreatedAt().atOffset(ZoneOffset.UTC));
            stmt.executeUpdate();
            return task;
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao salvar task no PostgreSQL", e);
        }
    }

    @Override
    public Optional<Task> update(String id, UnaryOperator<Task> updateFunction) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(updateFunction, "updateFunction cannot be null");

        String selectForUpdateSql =
                "SELECT id, title, completed, created_at FROM tasks WHERE id = ? FOR UPDATE";
        String updateSql =
                "UPDATE tasks SET title = ?, completed = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                Task current;
                try (PreparedStatement selectStmt = conn.prepareStatement(selectForUpdateSql)) {
                    selectStmt.setString(1, id);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return Optional.empty();
                        }
                        current = mapRow(rs);
                    }
                }

                Task updated = Objects.requireNonNull(
                        updateFunction.apply(current),
                        "updateFunction cannot return null");

                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, updated.getTitle());
                    updateStmt.setBoolean(2, updated.isCompleted());
                    updateStmt.setString(3, updated.getId());
                    updateStmt.executeUpdate();
                }

                conn.commit();
                return Optional.of(updated);
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                if (e instanceof RuntimeException runtimeException && !(e instanceof SQLException)) {
                    throw runtimeException;
                }
                throw new RepositoryException("Falha ao atualizar task de forma transacional no PostgreSQL", e);
            } finally {
                try {
                    conn.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao abrir transação para atualizar task no PostgreSQL", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        String sql = "DELETE FROM tasks WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao remover task por ID no PostgreSQL", e);
        }
    }

    @Override
    public boolean existsById(String id) {
        String sql = "SELECT 1 FROM tasks WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao verificar existência de task no PostgreSQL", e);
        }
    }

    private Task mapRow(ResultSet rs) throws SQLException {
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        return new Task(
                rs.getString("id"),
                rs.getString("title"),
                rs.getBoolean("completed"),
                createdAt.toInstant()
        );
    }

    @Override
    public void close() {
        // O ciclo de vida do DataSource (HikariCP) é gerenciado pelo composição (Main / DatabaseConfig).
    }
}
