package com.korofin.backend.dto.ai;

/**
 * Respuesta de {@code POST /api/ai/categorize}.
 *
 * @param categoryId   identificador de la categoría encontrada, o {@code null} cuando el usuario
 *                     todavía no tiene categorías de ese tipo, o el asistente no encontró una
 *                     coincidencia adecuada
 * @param categoryName nombre de la categoría encontrada, o {@code null} bajo las mismas
 *                     condiciones que {@link #categoryId}
 */
public record CategorizeResponse(
        Long categoryId,
        String categoryName
) {
}
