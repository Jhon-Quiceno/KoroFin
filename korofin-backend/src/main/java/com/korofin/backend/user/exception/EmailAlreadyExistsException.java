package com.korofin.backend.user.exception;

/**
 * Se lanza al intentar registrar o actualizar un usuario con un correo ya usado por otra cuenta.
 * Mapeada a {@code 409 Conflict}.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
