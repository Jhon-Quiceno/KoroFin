package com.korofin.backend.statement.exception;

/**
 * El texto extraído del archivo de extracto quedó en blanco (por ejemplo, un PDF escaneado como
 * imagen sin capa de texto), por lo que no hay nada que enviarle al proveedor de IA.
 *
 * <p>Mapeada a {@code HTTP 422 Unprocessable Entity} por {@code GlobalExceptionHandler}: el
 * request es válido, su contenido no se pudo procesar.
 */
public class EmptyStatementTextException extends RuntimeException {

    public EmptyStatementTextException(String message) {
        super(message);
    }
}
