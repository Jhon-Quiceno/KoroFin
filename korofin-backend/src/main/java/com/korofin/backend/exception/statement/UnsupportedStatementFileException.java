package com.korofin.backend.exception.statement;

/**
 * El archivo subido no tiene una extensión soportada por
 * {@code StatementTextExtractionService} ({@code .pdf}, {@code .csv} o {@code .xlsx}), o el
 * archivo está vacío.
 *
 * <p>Mapeada a {@code HTTP 400} por {@code GlobalExceptionHandler}: es un error del cliente, no
 * una falla del proceso de extracción en sí.
 */
public class UnsupportedStatementFileException extends RuntimeException {

    public UnsupportedStatementFileException(String message) {
        super(message);
    }
}
