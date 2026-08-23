package com.korofin.backend.exception.user;

/**
 * Se lanza cuando el correo/contraseña de login, o la contraseña actual en un cambio de
 * contraseña, no son válidos. Mapeada a {@code 401 Unauthorized}.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
