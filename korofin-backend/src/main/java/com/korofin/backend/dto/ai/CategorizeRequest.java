package com.korofin.backend.dto.ai;

import com.korofin.backend.entity.expense.CategoryType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload de {@code POST /api/ai/categorize}: le pide al asistente que sugiera cuál de las
 * categorías existentes del usuario (ingreso o gasto) corresponde mejor a una descripción libre.
 *
 * @param description descripción libre del movimiento a clasificar
 * @param amount      monto opcional, incluido en el prompt como contexto adicional cuando está
 *                    presente
 * @param type        tipo de categoría a sugerir ({@link CategoryType#INCOME} o
 *                    {@link CategoryType#EXPENSE}); opcional, por defecto {@code EXPENSE} cuando
 *                    está ausente
 */
public record CategorizeRequest(
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
        String description,
        @Positive(message = "El monto debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El monto no puede tener más de 2 decimales")
        BigDecimal amount,
        CategoryType type
) {
    public CategorizeRequest {
        if (type == null) {
            type = CategoryType.EXPENSE;
        }
    }
}
