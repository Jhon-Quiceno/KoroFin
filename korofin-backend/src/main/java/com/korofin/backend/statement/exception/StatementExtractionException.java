package com.korofin.backend.statement.exception;

/**
 * Falla genérica al extraer los movimientos de un extracto bancario: el archivo se pudo leer pero
 * no se pudo parsear (formato inesperado), o el proveedor de IA respondió con algo que
 * {@code StatementAiExtractionService} no pudo interpretar como una lista de movimientos válida.
 *
 * <p>Mapeada a {@code HTTP 422 Unprocessable Entity} por {@code GlobalExceptionHandler}.
 */
public class StatementExtractionException extends RuntimeException {

    public StatementExtractionException(String message) {
        super(message);
    }

    public StatementExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
