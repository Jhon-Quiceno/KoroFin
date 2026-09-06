package com.korofin.backend.ai.dto;

import com.korofin.backend.expense.entity.CategoryType;

/**
 * Resultado de {@code AiCategorizationService#classifyMovement}: qué tipo de movimiento (ingreso
 * o gasto) decidió el asistente que representa una descripción libre, más la categoría
 * encontrada de ese tipo decidido, si la hay.
 *
 * @param type         el tipo de movimiento decidido; cae a {@link CategoryType#EXPENSE} cuando la
 *                     respuesta del asistente no se pudo interpretar
 * @param categoryId   identificador de la categoría encontrada, o {@code null} cuando el usuario
 *                     no tiene categorías de {@link #type} todavía, o no se encontró coincidencia
 * @param categoryName nombre de la categoría encontrada, o {@code null} bajo las mismas
 *                     condiciones que {@link #categoryId}
 */
public record MovementClassification(
        CategoryType type,
        Long categoryId,
        String categoryName
) {
}
