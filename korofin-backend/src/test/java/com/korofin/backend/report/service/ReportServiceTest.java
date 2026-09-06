package com.korofin.backend.report.service;

import com.korofin.backend.analysis.dto.CategoryTotalResponse;
import com.korofin.backend.analysis.dto.FinancialSummaryResponse;
import com.korofin.backend.report.dto.MonthlyReportResponse;
import com.korofin.backend.report.dto.MovementResponse;
import com.korofin.backend.report.dto.MovementType;
import com.korofin.backend.expense.entity.Category;
import com.korofin.backend.expense.entity.Expense;
import com.korofin.backend.expense.entity.PaymentMethodType;
import com.korofin.backend.income.entity.Income;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.income.repository.IncomeRepository;
import com.korofin.backend.analysis.service.FinancialAnalysisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private FinancialAnalysisService financialAnalysisService;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(financialAnalysisService, expenseRepository, incomeRepository);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMonthlyReportDelegatesEntirelyToFinancialAnalysisServiceWithoutRecalculating() {
        YearMonth period = YearMonth.of(2026, 7);
        FinancialSummaryResponse summary = new FinancialSummaryResponse(
                2026, 7, BigDecimal.valueOf(1000), BigDecimal.valueOf(600), BigDecimal.valueOf(400),
                BigDecimal.valueOf(40), List.of(new CategoryTotalResponse(1L, "Comida", BigDecimal.valueOf(300))),
                List.of()
        );
        when(financialAnalysisService.getSummary(USER_ID, period)).thenReturn(summary);

        MonthlyReportResponse report = reportService.getMonthlyReport(period);

        assertThat(report.totalIncome()).isEqualByComparingTo("1000");
        assertThat(report.totalExpense()).isEqualByComparingTo("600");
        assertThat(report.topExpenseCategories()).hasSize(1);
        verify(expenseRepository, never()).sumAmountByUserAndPeriod(eq(USER_ID), any(), any());
        verify(incomeRepository, never()).sumAmountByUserAndPeriod(eq(USER_ID), any(), any());
    }

    @Test
    void getMovementsCombinesExpensesAndIncomesSortedByDateDescending() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);

        Expense expense = newExpense(1L, BigDecimal.valueOf(50), LocalDate.of(2026, 7, 10), null);
        Income income = newIncome(2L, BigDecimal.valueOf(2000), LocalDate.of(2026, 7, 20), null);

        when(expenseRepository.findByUser_IdAndDateBetween(USER_ID, from, to)).thenReturn(List.of(expense));
        when(incomeRepository.findByUser_IdAndDateBetween(USER_ID, from, to)).thenReturn(List.of(income));

        List<MovementResponse> movements = reportService.getMovements(from, to);

        assertThat(movements).hasSize(2);
        assertThat(movements.get(0).type()).isEqualTo(MovementType.INCOME);
        assertThat(movements.get(0).date()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(movements.get(1).type()).isEqualTo(MovementType.EXPENSE);
        assertThat(movements.get(1).categoryName()).isEqualTo("Sin categoría");
    }

    private static Expense newExpense(Long id, BigDecimal amount, LocalDate date, Category category) {
        Expense expense = new Expense();
        expense.setId(id);
        expense.setAmount(amount);
        expense.setDate(date);
        expense.setDescription("Gasto de prueba");
        expense.setPaymentMethod(PaymentMethodType.CASH);
        expense.setCategory(category);
        return expense;
    }

    private static Income newIncome(Long id, BigDecimal amount, LocalDate date, Category category) {
        Income income = new Income();
        income.setId(id);
        income.setAmount(amount);
        income.setDate(date);
        income.setDescription("Ingreso de prueba");
        income.setCategory(category);
        return income;
    }
}
