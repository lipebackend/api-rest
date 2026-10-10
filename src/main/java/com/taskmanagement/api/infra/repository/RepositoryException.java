package com.taskmanagement.api.infra.repository;

/**
 * Exceção de infraestrutura lançada quando ocorre uma falha de acesso ao banco de dados (JDBC/SQL).
 * Mapeada na camada HTTP para 503 Service Unavailable sem expor detalhes internos de SQL ao cliente.
 */
public class RepositoryException extends RuntimeException {

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
