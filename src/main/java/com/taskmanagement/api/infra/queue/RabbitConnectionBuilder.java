package com.taskmanagement.api.infra.queue;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class RabbitConnectionBuilder {
    private RabbitConfig config;
    private String connectionName;

    
    public RabbitConnectionBuilder config(RabbitConfig config) {
        this.config = config;
        return this;
    }

    public RabbitConfig getConfig() {
        return config;
    }

    public RabbitConnectionBuilder connectionName(String name) {
        this.connectionName = name;
        return this;
    }

    public Connection build() throws IOException, TimeoutException {
        if (this.config == null) {
            throw new IllegalArgumentException("RabbitConfig is required");
        }
        if (this.connectionName == null || this.connectionName.isBlank()) {
            throw new IllegalArgumentException("Connection name is required");
        }

        // 1. Usa a ConnectionFactory oficial do RabbitMQ por baixo dos panos
        ConnectionFactory rabbitFactory = new ConnectionFactory();
        rabbitFactory.setHost(this.config.host());
        rabbitFactory.setPort(this.config.port());
        rabbitFactory.setUsername(this.config.username());
        rabbitFactory.setPassword(this.config.password());
        rabbitFactory.setConnectionTimeout(this.config.connectionTimeoutMs());
        rabbitFactory.setAutomaticRecoveryEnabled(true);
        rabbitFactory.setTopologyRecoveryEnabled(true);

        // 2. Abre e retorna a Connection TCP real!
        return rabbitFactory.newConnection(this.connectionName);
    }
}
