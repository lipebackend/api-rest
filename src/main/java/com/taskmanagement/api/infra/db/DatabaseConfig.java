package com.taskmanagement.api.infra.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * Configuração do pool de conexões JDBC (HikariCP) e execução de migrações Flyway.
 */
public final class DatabaseConfig {

    private static volatile HikariDataSource dataSource;

    private DatabaseConfig() {
    }

    public static synchronized DataSource getDataSource(String url, String username, String password) {
        if (dataSource == null || dataSource.isClosed()) {
            dataSource = createDataSource(url, username, password);
        }
        return dataSource;
    }

    public static HikariDataSource createDataSource(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(3000);
        return new HikariDataSource(config);
    }

    public static void runMigrations(DataSource targetDataSource) {
        Flyway.configure()
                .dataSource(targetDataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    public static synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null;
        }
    }
}