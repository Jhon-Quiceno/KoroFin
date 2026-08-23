package com.korofin.backend.exception.statement;

/**
 * El PDF del extracto está protegido con contraseña y la contraseña provista (o la ausencia de
 * una) no permitió abrirlo.
 *
 * <p>Mapeada a {@code HTTP 422 Unprocessable Entity} por {@code GlobalExceptionHandler}: el
 * archivo es válido, pero no se pudo procesar con los datos provistos.
 */
public class StatementPasswordException extends RuntimeException {

    public StatementPasswordException(String message) {
        super(message);
    }

    public StatementPasswordException(String message, Throwable cause) {
        super(message, cause);
    }
}
