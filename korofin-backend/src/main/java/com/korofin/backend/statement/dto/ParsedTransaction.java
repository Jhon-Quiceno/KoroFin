package com.korofin.backend.statement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un movimiento extraído y normalizado de un extracto bancario, antes de mostrarse en la
 * previsualización o de persistirse como {@code Income}/{@code Expense}.
 *
 * <p>Producido por {@code StatementAiExtractionService#extract} a partir de la respuesta del
 * proveedor de IA, ya validado (fecha parseable, monto positivo, tipo de movimiento reconocido) y
 * con la categoría sugerida resuelta contra las categorías reales del usuario.
 *
 * @param date                  fecha del movimiento
 * @param description           descripción tal como aparece en el extracto
 * @param amount                monto del movimiento, siempre positivo (el signo lo da {@link #movementType})
 * @param movementType          si el movimiento es un ingreso o un gasto
 * @param suggestedCategoryId   id de la categoría del usuario sugerida por la IA, o {@code null} si no hay coincidencia
 * @param suggestedCategoryName nombre canónico de la categoría sugerida, o {@code null} si no hay coincidencia
 */
public record ParsedTransaction(
        LocalDate date,
        String description,
        BigDecimal amount,
        MovementType movementType,
        Long suggestedCategoryId,
        String suggestedCategoryName
) {
}
