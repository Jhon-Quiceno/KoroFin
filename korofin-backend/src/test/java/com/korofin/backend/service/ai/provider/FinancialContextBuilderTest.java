package com.korofin.backend.service.ai.provider;

import com.korofin.backend.entity.debt.Debt;
import com.korofin.backend.entity.expense.Category;
import com.korofin.backend.entity.expense.Expense;
import com.korofin.backend.entity.expense.PaymentMethodType;
import com.korofin.backend.entity.income.Income;
import com.korofin.backend.repository.common.MonthlyTotalProjection;
import com.korofin.backend.repository.debt.DebtRepository;
import com.korofin.backend.repository.expense.CategoryTotalProjection;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.income.IncomeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialContextBuilderTest {

    /** "Ahora" fijo: julio 2026, UTC — mismo período que {@code AiChatServiceTest}. */
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-09T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private DebtRepository debtRepository;

    private FinancialContextBuilder contextBuilder;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        contextBuilder = new FinancialContextBuilder(expenseRepository, incomeRepository, debtRepository, FIXED_CLOCK);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void buildSystemPromptShouldIncludeSummaryTopCategoriesTransactionsAndActiveDebtsOnly() {
        setAuthenticatedUser(1L);
        when(incomeRepository.sumAmountByUserGroupedByMonth(any(), any(), any()))
                .thenReturn(List.of(monthlyTotal(2026, 7, BigDecimal.valueOf(5_000_000))));
        when(expenseRepository.sumAmountByUserGroupedByMonth(any(), any(), any()))
                .thenReturn(List.of(monthlyTotal(2026, 7, BigDecimal.valueOf(3_000_000))));
        when(debtRepository.sumRemainingAmountByUser(1L)).thenReturn(BigDecimal.valueOf(1_000_000));
        when(expenseRepository.findTopCategoriesByUserAndPeriod(any(), any(), any()))
                .thenReturn(List.of(categoryTotal(10L, "Comida", BigDecimal.valueOf(800_000))));

        Debt activeDebt = new Debt();
        activeDebt.setName("Tarjeta de crédito");
        activeDebt.setRemainingAmount(BigDecimal.valueOf(500_000));
        activeDebt.setDueDate(LocalDate.of(2026, 7, 15));
        Debt paidOffDebt = new Debt();
        paidOffDebt.setName("Préstamo pagado");
        paidOffDebt.setRemainingAmount(BigDecimal.ZERO);
        when(debtRepository.findAllByUser_Id(1L)).thenReturn(List.of(activeDebt, paidOffDebt));

        Expense expense = new Expense();
        expense.setDate(LocalDate.of(2026, 7, 1));
        expense.setAmount(BigDecimal.valueOf(50_000));
        expense.setDescription("Almuerzo");
        Category comida = new Category();
        comida.setName("Comida");
        expense.setCategory(comida);
        expense.setPaymentMethod(PaymentMethodType.CASH);
        when(expenseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(expensePage(List.of(expense)));
        when(incomeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(incomePage(List.of()));

        String prompt = contextBuilder.buildSystemPrompt();

        Assertions.assertTrue(prompt.contains("Responde siempre en español"));
        Assertions.assertTrue(prompt.contains(
                "Solo puedo ayudarte con preguntas sobre tu situación financiera registrada en KoroFin"));
        Assertions.assertTrue(prompt.contains("5.000.000"), prompt);
        Assertions.assertTrue(prompt.contains("3.000.000"), prompt);
        Assertions.assertTrue(prompt.contains("Comida"));
        Assertions.assertTrue(prompt.contains("Almuerzo"));
        Assertions.assertTrue(prompt.contains("Tarjeta de crédito"));
        Assertions.assertTrue(prompt.contains("500.000"));
        Assertions.assertFalse(prompt.contains("Préstamo pagado"), "las deudas ya saldadas deben excluirse");
        Assertions.assertTrue(prompt.contains("2026-07-15"));
    }

    @Test
    void buildSystemPromptShouldHandleEmptyDataGracefully() {
        setAuthenticatedUser(2L);
        when(incomeRepository.sumAmountByUserGroupedByMonth(any(), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByUserGroupedByMonth(any(), any(), any())).thenReturn(List.of());
        when(debtRepository.sumRemainingAmountByUser(2L)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.findTopCategoriesByUserAndPeriod(any(), any(), any())).thenReturn(List.of());
        when(debtRepository.findAllByUser_Id(2L)).thenReturn(List.of());
        when(expenseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(expensePage(List.of()));
        when(incomeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(incomePage(List.of()));

        String prompt = contextBuilder.buildSystemPrompt();

        Assertions.assertTrue(prompt.contains("no tiene deudas activas"));
        Assertions.assertTrue(prompt.contains("No hay movimientos recientes"));
        Assertions.assertTrue(prompt.contains("No hay datos de categorías"));
    }

    @Test
    void buildSystemPromptShouldMergeRecentExpensesAndIncomesSortedByDateDescending() {
        setAuthenticatedUser(3L);
        when(incomeRepository.sumAmountByUserGroupedByMonth(any(), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByUserGroupedByMonth(any(), any(), any())).thenReturn(List.of());
        when(debtRepository.sumRemainingAmountByUser(3L)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.findTopCategoriesByUserAndPeriod(any(), any(), any())).thenReturn(List.of());
        when(debtRepository.findAllByUser_Id(3L)).thenReturn(List.of());

        Expense olderExpense = new Expense();
        olderExpense.setDate(LocalDate.of(2026, 7, 1));
        olderExpense.setAmount(BigDecimal.valueOf(10_000));
        olderExpense.setPaymentMethod(PaymentMethodType.CASH);
        Income newerIncome = new Income();
        newerIncome.setDate(LocalDate.of(2026, 7, 5));
        newerIncome.setAmount(BigDecimal.valueOf(2_000_000));
        newerIncome.setDescription("Salario");
        when(expenseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(expensePage(List.of(olderExpense)));
        when(incomeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(incomePage(List.of(newerIncome)));

        String prompt = contextBuilder.buildSystemPrompt();

        int incomeIndex = prompt.indexOf("Salario");
        int expenseIndex = prompt.indexOf("2026-07-01");
        Assertions.assertTrue(incomeIndex >= 0 && expenseIndex >= 0);
        Assertions.assertTrue(incomeIndex < expenseIndex, "el ingreso más reciente debe listarse antes que el gasto más viejo");
        Assertions.assertTrue(prompt.contains("Ingreso"));
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }

    private static MonthlyTotalProjection monthlyTotal(int year, int month, BigDecimal total) {
        return new MonthlyTotalProjection() {
            @Override
            public Integer getPeriodYear() {
                return year;
            }

            @Override
            public Integer getPeriodMonth() {
                return month;
            }

            @Override
            public BigDecimal getTotal() {
                return total;
            }
        };
    }

    private static Page<Expense> expensePage(List<Expense> content) {
        return new PageImpl<>(content);
    }

    private static Page<Income> incomePage(List<Income> content) {
        return new PageImpl<>(content);
    }

    private static CategoryTotalProjection categoryTotal(Long categoryId, String categoryName, BigDecimal total) {
        return new CategoryTotalProjection() {
            @Override
            public Long getCategoryId() {
                return categoryId;
            }

            @Override
            public String getCategoryName() {
                return categoryName;
            }

            @Override
            public BigDecimal getTotal() {
                return total;
            }
        };
    }
}
