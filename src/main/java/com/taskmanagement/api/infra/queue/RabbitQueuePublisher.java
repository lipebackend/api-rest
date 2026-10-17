package com.taskmanagement.api.infra.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.taskmanagement.api.domain.contract.queue.TaskQueuePublisher;
import com.taskmanagement.api.domain.model.Task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Publicador de eventos no RabbitMQ com reutilização thread-safe de {@link Channel}
 * e confirmação de publicação (*Publisher Confirms*).
 */
public class RabbitQueuePublisher implements TaskQueuePublisher, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(RabbitQueuePublisher.class.getName());
    private static final long CONFIRM_TIMEOUT_MS = 5_000L;

    public static final String TASK_CREATED_QUEUE = "task.created";
    public static final String TASK_UPDATED_QUEUE = "task.updated";
    public static final String TASK_DELETED_QUEUE = "task.deleted";

    private final Connection connection;
    private final ObjectMapper objectMapper;
    private final Object channelLock = new Object();
    private Channel sharedChannel;

    public RabbitQueuePublisher(Connection connection, ObjectMapper objectMapper) {
        this.connection = Objects.requireNonNull(connection, "connection não pode ser nula");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper não pode ser nulo");
        ensureChannelAndTopology();
    }

    private Channel ensureChannelAndTopology() {
        synchronized (channelLock) {
            try {
                if (sharedChannel == null || !sharedChannel.isOpen()) {
                    sharedChannel = connection.createChannel();
                    sharedChannel.confirmSelect();
                    sharedChannel.queueDeclare(TASK_CREATED_QUEUE, true, false, false, null);
                    sharedChannel.queueDeclare(TASK_UPDATED_QUEUE, true, false, false, null);
                    sharedChannel.queueDeclare(TASK_DELETED_QUEUE, true, false, false, null);
                }
                return sharedChannel;
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "Não foi possível inicializar canal/topologia no RabbitMQ", exception);
                return null;
            }
        }
    }

    @Override
    public void publishTaskCreatedEvent(Task task) {
        publish(TASK_CREATED_QUEUE, Objects.requireNonNull(task, "task não pode ser nula"));
    }

    @Override
    public void publishTaskUpdatedEvent(Task task) {
        publish(TASK_UPDATED_QUEUE, Objects.requireNonNull(task, "task não pode ser nula"));
    }

    @Override
    public void publishTaskDeletedEvent(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId não pode ser nulo ou vazio");
        }
        publish(TASK_DELETED_QUEUE, Map.of("id", taskId));
    }

    private void publish(String queue, Object payload) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Erro ao serializar evento para a fila " + queue, exception);
        }

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .contentEncoding(StandardCharsets.UTF_8.name())
                .deliveryMode(2)
                .type(queue)
                .build();

        synchronized (channelLock) {
            try {
                Channel channel = ensureChannelAndTopology();
                if (channel == null) {
                    throw new IllegalStateException("Canal AMQP indisponível para a fila " + queue);
                }
                channel.basicPublish("", queue, props, body);
                channel.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            } catch (Exception exception) {
                closeChannelQuietly();
                throw new IllegalStateException("Falha ao publicar evento na fila RabbitMQ: " + queue, exception);
            }
        }
    }

    private void closeChannelQuietly() {
        if (sharedChannel != null) {
            try {
                if (sharedChannel.isOpen()) {
                    sharedChannel.close();
                }
            } catch (IOException | TimeoutException ignored) {
            } finally {
                sharedChannel = null;
            }
        }
    }

    @Override
    public void close() {
        synchronized (channelLock) {
            closeChannelQuietly();
            try {
                if (connection.isOpen()) {
                    connection.close();
                }
            } catch (IOException exception) {
                LOGGER.log(Level.WARNING, "Falha ao fechar conexão com RabbitMQ de forma limpa", exception);
            }
        }
    }
}
