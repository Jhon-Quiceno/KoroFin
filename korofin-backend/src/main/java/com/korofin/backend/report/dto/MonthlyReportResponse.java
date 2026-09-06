package com.korofin.backend.report.dto;

import com.korofin.backend.analysis.dto.CategoryTotalResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cifras del mes para {@code GET /api/reports/monthly}. Reordena exactamente la misma forma que
 * {@code FinancialAnalysisService#getSummary} devuelve — {@code ReportService} no recalcula nada
 * (ver docs/backend-plan.md sección 7).
 *
 * @param periodYear          año calendario del período
 * @param periodMonth         mes calendario del período (1-12)
 * @param totalIncome         total de ingresos del período
 * @param totalExpense        total de gastos del período
 * @param totalSavings        {@code totalIncome - totalExpense}
 * @param savingsRate         porcentaje de ahorro sobre el ingreso (0-100)
 * @param topExpenseCategories categorías de gasto del período, ordenadas de mayor a menor total
 */
public record MonthlyReportResponse(
        int periodYear,
        int periodMonth,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal totalSavings,
        BigDecimal savingsRate,
        List<CategoryTotalResponse> topExpenseCategories
) {
}
