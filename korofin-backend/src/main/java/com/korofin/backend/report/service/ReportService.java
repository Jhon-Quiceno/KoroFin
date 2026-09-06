package com.korofin.backend.report.service;

import com.korofin.backend.analysis.dto.FinancialSummaryResponse;
import com.korofin.backend.report.dto.MonthlyReportResponse;
import com.korofin.backend.report.dto.MovementResponse;
import com.korofin.backend.report.dto.MovementType;
import com.korofin.backend.expense.entity.Category;
import com.korofin.backend.expense.entity.Expense;
import com.korofin.backend.income.entity.Income;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.income.repository.IncomeRepository;
import com.korofin.backend.common.security.SecurityUtils;
import com.korofin.backend.analysis.service.FinancialAnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lógica de negocio de {@code GET /api/reports/*}.
 *
 * <p>{@link #getMonthlyReport(YearMonth)} delega ÍNTEGRAMENTE en
 * {@link FinancialAnalysisService#getSummary(Long, YearMonth)} — <b>no recalcula nada</b>, solo
 * reordena la forma de la respuesta (ver docs/backend-plan.md sección 7). {@link #getMovements}
 * combina {@code Expense}+{@code Income} del período, ordenados por fecha descendente.
 */
@Service
public class ReportService {

    private final FinancialAnalysisService financialAnalysisService;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    public ReportService(
            FinancialAnalysisService financialAnalysisService,
            ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository
    ) {
        this.financialAnalysisService = financialAnalysisService;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
    }

    @Transactional
    public MonthlyReportResponse getMonthlyReport(YearMonth period) {
        Long userId = SecurityUtils.getCurrentUserId();
        FinancialSummaryResponse summary = financialAnalysisService.getSummary(userId, period);

        return new MonthlyReportResponse(
                summary.periodYear(), summary.periodMonth(),
                summary.totalIncome(), summary.totalExpense(), summary.totalSavings(), summary.savingsRate(),
                summary.topExpenseCategories()
        );
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> getMovements(LocalDate from, LocalDate to) {
        Long userId = SecurityUtils.getCurrentUserId();

        List<MovementResponse> movements = new ArrayList<>();
        expenseRepository.findByUser_IdAndDateBetween(userId, from, to)
                .forEach(expense -> movements.add(toMovementResponse(expense)));
        incomeRepository.findByUser_IdAndDateBetween(userId, from, to)
                .forEach(income -> movements.add(toMovementResponse(income)));

        movements.sort(Comparator.comparing(MovementResponse::date).reversed());
        return movements;
    }

    private static MovementResponse toMovementResponse(Expense expense) {
        return new MovementResponse(
                expense.getId(), MovementType.EXPENSE, expense.getDate(), expense.getAmount(),
                expense.getDescription(), categoryName(expense.getCategory())
        );
    }

    private static MovementResponse toMovementResponse(Income income) {
        return new MovementResponse(
                income.getId(), MovementType.INCOME, income.getDate(), income.getAmount(),
                income.getDescription(), categoryName(income.getCategory())
        );
    }

    private static String categoryName(Category category) {
        return category == null ? "Sin categoría" : category.getName();
    }
}
