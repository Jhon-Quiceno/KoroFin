package com.korofin.backend.dto.analysis;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumen financiero completo de un mes calendario para el usuario actual, devuelto por
 * {@code FinancialAnalysisService#getSummary} — la única fuente de verdad de estas cifras
 * (ver docs/backend-plan.md sección 2.12); {@code ReportService} reordena esta misma forma en vez
 * de recalcular nada.
 *
 * @param periodYear          año calendario del período
 * @param periodMonth         mes calendario del período (1-12)
 * @param totalIncome         total de ingresos del período
 * @param totalExpense        total de gastos del período
 * @param totalSavings        {@code totalIncome - totalExpense}, puede ser negativo
 * @param savingsRate         porcentaje de ahorro sobre el ingreso (0-100), cero cuando no hubo ingresos
 * @param topExpenseCategories categorías de gasto del período, ordenadas de mayor a menor total
 * @param monthlySeries       serie de los últimos 6 meses (incluido el período pedido), sin huecos
 */
public record FinancialSummaryResponse(
        int periodYear,
        int periodMonth,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal totalSavings,
        BigDecimal savingsRate,
        List<CategoryTotalResponse> topExpenseCategories,
        List<MonthlyTotalResponse> monthlySeries
) {
}
