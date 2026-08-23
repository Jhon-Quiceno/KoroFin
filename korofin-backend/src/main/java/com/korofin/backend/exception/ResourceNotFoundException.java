package com.korofin.backend.exception;

/**
 * Se lanza cuando se busca un recurso por identificador y no existe. Mapeada a
 * {@code 404 Not Found} por {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
