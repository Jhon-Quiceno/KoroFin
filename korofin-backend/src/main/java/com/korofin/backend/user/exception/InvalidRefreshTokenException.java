package com.korofin.backend.user.exception;

/**
 * Se lanza cuando un refresh token es inválido, expiró o ya fue revocado. Mapeada a
 * {@code 401 Unauthorized}.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
