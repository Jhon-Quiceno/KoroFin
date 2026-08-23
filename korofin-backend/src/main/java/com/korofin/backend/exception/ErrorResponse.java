package com.korofin.backend.exception;

import java.time.Instant;

/**
 * Cuerpo estándar de respuesta de error para todas las excepciones mapeadas por
 * {@link GlobalExceptionHandler}.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
