package com.korofin.backend.dto.ai;

import com.korofin.backend.entity.expense.CategoryType;

/**
 * Resultado de {@code FinancialSummaryQueryService#parseQuery}: qué está pidiendo una pregunta de
 * resumen financiero en lenguaje natural (p. ej. {@code "¿Cuánto gasté en comida este mes?"}).
 *
 * @param period       el rango de fechas al que se refiere la pregunta; por defecto
 *                      {@link SummaryPeriod#MONTH} cuando no se menciona un período explícito
 * @param movementType {@link CategoryType#EXPENSE} para una pregunta de gasto,
 *                      {@link CategoryType#INCOME} para una de ingreso, o {@code null} para una
 *                      pregunta de balance/general que cubre ambos
 * @param categoryName nombre libre de la categoría mencionada en la pregunta (p. ej.
 *                      {@code "comida"}), o {@code null} cuando no se nombró ninguna categoría
 *                      específica
 * @param topic        si la pregunta es sobre deudas o sobre movimientos; por defecto
 *                      {@link SummaryTopic#MOVEMENT} cuando no se pudo decidir el tema
 */
public record SummaryQueryIntent(
        SummaryPeriod period,
        CategoryType movementType,
        String categoryName,
        SummaryTopic topic
) {
}
