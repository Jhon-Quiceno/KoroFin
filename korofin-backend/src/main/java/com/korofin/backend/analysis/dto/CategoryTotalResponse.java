package com.korofin.backend.analysis.dto;

import java.math.BigDecimal;

/**
 * Un total agrupado por categoría dentro de un {@link FinancialSummaryResponse}.
 *
 * @param categoryId   identificador de la categoría, o {@code null} para el agregado "sin categoría"
 * @param categoryName nombre de la categoría, o {@code "Sin categoría"} para el agregado sin clasificar
 * @param total        monto total gastado en esa categoría durante el período
 */
public record CategoryTotalResponse(
        Long categoryId,
        String categoryName,
        BigDecimal total
) {
}
