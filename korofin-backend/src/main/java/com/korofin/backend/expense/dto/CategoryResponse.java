package com.korofin.backend.expense.dto;

import com.korofin.backend.expense.entity.CategoryType;

/**
 * Modelo de lectura de una {@link com.korofin.backend.expense.entity.Category}.
 *
 * @param id   identificador de la categoría
 * @param name nombre de la categoría
 * @param type si la categoría clasifica gastos o ingresos
 */
public record CategoryResponse(
        Long id,
        String name,
        CategoryType type
) {
}
