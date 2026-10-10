package com.taskmanagement.api.infra.db;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseConfig {
    private static HikariDataSource dataSource;

    private DatabaseConfig() {}
    
    public static DataSource getDataSource(String url, String username, String password) {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();

            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            config.setConnectionTimeout(3000);

            dataSource = new HikariDataSource(config);
        }
        
        return dataSource;
    }

    public static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}