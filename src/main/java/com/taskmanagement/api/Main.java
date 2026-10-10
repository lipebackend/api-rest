package com.taskmanagement.api;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanagement.api.domain.contract.repository.TaskRepository;
import com.taskmanagement.api.domain.contract.services.TaskService;
import com.taskmanagement.api.domain.service.TaskServiceImpl;
import com.taskmanagement.api.infra.config.ServerConfig;
import com.taskmanagement.api.infra.db.DatabaseConfig;
import com.taskmanagement.api.infra.repository.InMemoryTaskRepository;
import com.taskmanagement.api.infra.web.http.Server;
import com.taskmanagement.api.infra.web.http.handler.TaskHandler;
import com.taskmanagement.api.infra.web.json.JsonMapper;

public class Main {
    public static void main(String[] args) throws IOException {
        ServerConfig config = ServerConfig.fromEnv(args);
        Server server = new Server(config.host(), config.port());

        TaskRepository taskRepository = new InMemoryTaskRepository();
        TaskService taskService = new TaskServiceImpl(taskRepository);
        ObjectMapper objectMapper = JsonMapper.create();
        TaskHandler taskHandler = new TaskHandler(taskService, objectMapper);



        server.createContext("/tasks", taskHandler);

        // Registrar shutdown hook para fechamento ordenado do servidor
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nEncerrando servidor de forma ordenada...");
            server.close();
            DatabaseConfig.close();
            System.out.println("Servidor finalizado.");
        }, "shutdown-hook"));

        server.start();
    }

}
