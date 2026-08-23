package com.korofin.backend.dto.analysis;

import java.math.BigDecimal;

/**
 * Un punto de la serie mensual de ingresos/gastos dentro de un {@link FinancialSummaryResponse}.
 * Un mes sin movimientos aparece igual, con {@code income}/{@code expense} en cero — la serie
 * siempre cubre el rango completo pedido, sin huecos.
 *
 * @param periodYear  año calendario del punto
 * @param periodMonth mes calendario del punto (1-12)
 * @param income      total de ingresos de ese mes
 * @param expense     total de gastos de ese mes
 */
public record MonthlyTotalResponse(
        int periodYear,
        int periodMonth,
        BigDecimal income,
        BigDecimal expense
) {
}
