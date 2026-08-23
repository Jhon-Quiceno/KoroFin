package com.korofin.backend.dto.expense;

import com.korofin.backend.entity.expense.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload para crear o actualizar una {@link com.korofin.backend.entity.expense.Category}.
 *
 * @param name nombre de la categoría, único por usuario y tipo
 * @param type si la categoría clasifica gastos o ingresos
 */
public record CategoryRequest(
        @NotBlank(message = "El nombre de la categoría es obligatorio")
        @Size(max = 100, message = "El nombre de la categoría no puede superar 100 caracteres")
        String name,
        @NotNull(message = "El tipo de categoría es obligatorio")
        CategoryType type
) {
}
