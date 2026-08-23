package com.korofin.backend.dto.expense;

import com.korofin.backend.entity.expense.CategoryType;

/**
 * Modelo de lectura de una {@link com.korofin.backend.entity.expense.Category}.
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
