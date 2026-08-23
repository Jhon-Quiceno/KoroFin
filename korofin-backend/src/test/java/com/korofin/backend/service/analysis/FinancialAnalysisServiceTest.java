package com.korofin.backend.service.analysis;

import com.korofin.backend.dto.analysis.FinancialSummaryResponse;
import com.korofin.backend.dto.analysis.RecommendationResponse;
import com.korofin.backend.entity.analysis.FinancialAnalysis;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.repository.analysis.FinancialAnalysisRepository;
import com.korofin.backend.repository.common.MonthlyTotalProjection;
import com.korofin.backend.repository.expense.CategoryTotalProjection;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.income.IncomeRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialAnalysisServiceTest {

    private static final Long USER_ID = 1L;
    private static final YearMonth PERIOD = YearMonth.of(2026, 7);

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private FinancialAnalysisRepository financialAnalysisRepository;

    @Mock
    private UserRepository userRepository;

    private FinancialAnalysisService financialAnalysisService;

    @BeforeEach
    void setUp() {
        financialAnalysisService = new FinancialAnalysisService(
                expenseRepository, incomeRepository, financialAnalysisRepository, userRepository
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getSummaryComputesIncomeExpenseSavingsAndRate() {
        LocalDate start = PERIOD.atDay(1);
        LocalDate end = PERIOD.atEndOfMonth();
        when(incomeRepository.sumAmountByUserAndPeriod(USER_ID, start, end)).thenReturn(BigDecimal.valueOf(1000));
        when(expenseRepository.sumAmountByUserAndPeriod(USER_ID, start, end)).thenReturn(BigDecimal.valueOf(600));
        when(expenseRepository.findTopCategoriesByUserAndPeriod(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(incomeRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(financialAnalysisRepository.findByUser_IdAndPeriodYearAndPeriodMonth(USER_ID, 2026, 7))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());

        FinancialSummaryResponse summary = financialAnalysisService.getSummary(USER_ID, PERIOD);

        assertThat(summary.totalIncome()).isEqualByComparingTo("1000");
        assertThat(summary.totalExpense()).isEqualByComparingTo("600");
        assertThat(summary.totalSavings()).isEqualByComparingTo("400");
        assertThat(summary.savingsRate()).isEqualByComparingTo("40.00");
        assertThat(summary.periodYear()).isEqualTo(2026);
        assertThat(summary.periodMonth()).isEqualTo(7);
    }

    @Test
    void getSummaryReturnsZeroSavingsRateWhenIncomeIsZero() {
        LocalDate start = PERIOD.atDay(1);
        LocalDate end = PERIOD.atEndOfMonth();
        when(incomeRepository.sumAmountByUserAndPeriod(USER_ID, start, end)).thenReturn(null);
        when(expenseRepository.sumAmountByUserAndPeriod(USER_ID, start, end)).thenReturn(BigDecimal.valueOf(300));
        when(expenseRepository.findTopCategoriesByUserAndPeriod(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(incomeRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(financialAnalysisRepository.findByUser_IdAndPeriodYearAndPeriodMonth(USER_ID, 2026, 7))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());

        FinancialSummaryResponse summary = financialAnalysisService.getSummary(USER_ID, PERIOD);

        assertThat(summary.totalIncome()).isEqualByComparingTo("0");
        assertThat(summary.savingsRate()).isEqualByComparingTo("0");
    }

    @Test
    void getSummaryFillsMonthlySeriesGapsWithZero() {
        LocalDate start = PERIOD.atDay(1);
        LocalDate end = PERIOD.atEndOfMonth();
        when(incomeRepository.sumAmountByUserAndPeriod(USER_ID, start, end)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumAmountByUserAndPeriod(USER_ID, start, end)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.findTopCategoriesByUserAndPeriod(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(incomeRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(
                List.of(monthlyTotal(2026, 7, BigDecimal.valueOf(150)))
        );
        when(financialAnalysisRepository.findByUser_IdAndPeriodYearAndPeriodMonth(USER_ID, 2026, 7))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());

        FinancialSummaryResponse summary = financialAnalysisService.getSummary(USER_ID, PERIOD);

        assertThat(summary.monthlySeries()).hasSize(6);
        assertThat(summary.monthlySeries().get(0).expense()).isEqualByComparingTo("0");
        assertThat(summary.monthlySeries().get(5).periodYear()).isEqualTo(2026);
        assertThat(summary.monthlySeries().get(5).periodMonth()).isEqualTo(7);
        assertThat(summary.monthlySeries().get(5).expense()).isEqualByComparingTo("150");
    }

    @Test
    void getSummaryUpsertsExistingSnapshotInsteadOfCreatingANewOne() {
        LocalDate start = PERIOD.atDay(1);
        LocalDate end = PERIOD.atEndOfMonth();
        when(incomeRepository.sumAmountByUserAndPeriod(USER_ID, start, end)).thenReturn(BigDecimal.valueOf(500));
        when(expenseRepository.sumAmountByUserAndPeriod(USER_ID, start, end)).thenReturn(BigDecimal.valueOf(100));
        when(expenseRepository.findTopCategoriesByUserAndPeriod(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(incomeRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());

        FinancialAnalysis existing = new FinancialAnalysis();
        existing.setId(42L);
        when(financialAnalysisRepository.findByUser_IdAndPeriodYearAndPeriodMonth(USER_ID, 2026, 7))
                .thenReturn(Optional.of(existing));

        financialAnalysisService.getSummary(USER_ID, PERIOD);

        ArgumentCaptor<FinancialAnalysis> captor = ArgumentCaptor.forClass(FinancialAnalysis.class);
        verify(financialAnalysisRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(42L);
        assertThat(captor.getValue().getTotalIncome()).isEqualByComparingTo("500");
        verify(userRepository, never()).getReferenceById(any());
    }

    @Test
    void getSummaryWithoutExplicitUserIdUsesCurrentSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null));
        LocalDate start = PERIOD.atDay(1);
        LocalDate end = PERIOD.atEndOfMonth();
        when(incomeRepository.sumAmountByUserAndPeriod(USER_ID, start, end)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumAmountByUserAndPeriod(USER_ID, start, end)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.findTopCategoriesByUserAndPeriod(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(incomeRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(financialAnalysisRepository.findByUser_IdAndPeriodYearAndPeriodMonth(USER_ID, 2026, 7))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());

        FinancialSummaryResponse summary = financialAnalysisService.getSummary(PERIOD);

        assertThat(summary.periodYear()).isEqualTo(2026);
    }

    @Test
    void getRecommendationsWarnsAboutDeficitWhenSavingsAreNegative() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null));
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();
        when(incomeRepository.sumAmountByUserAndPeriod(eq(USER_ID), eq(start), eq(end))).thenReturn(BigDecimal.valueOf(500));
        when(expenseRepository.sumAmountByUserAndPeriod(eq(USER_ID), eq(start), eq(end))).thenReturn(BigDecimal.valueOf(800));
        when(expenseRepository.findTopCategoriesByUserAndPeriod(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(incomeRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(financialAnalysisRepository.findByUser_IdAndPeriodYearAndPeriodMonth(eq(USER_ID), any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());

        List<RecommendationResponse> recommendations = financialAnalysisService.getRecommendations();

        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations.get(0).title()).contains("gastando más");
    }

    @Test
    void getRecommendationsFlagsConcentratedCategorySpending() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null));
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();
        when(incomeRepository.sumAmountByUserAndPeriod(eq(USER_ID), eq(start), eq(end))).thenReturn(BigDecimal.valueOf(1000));
        when(expenseRepository.sumAmountByUserAndPeriod(eq(USER_ID), eq(start), eq(end))).thenReturn(BigDecimal.valueOf(500));
        when(expenseRepository.findTopCategoriesByUserAndPeriod(eq(USER_ID), any(), any())).thenReturn(
                List.of(categoryTotal(1L, "Comida", BigDecimal.valueOf(400)))
        );
        when(incomeRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByUserGroupedByMonth(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(financialAnalysisRepository.findByUser_IdAndPeriodYearAndPeriodMonth(eq(USER_ID), any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());

        List<RecommendationResponse> recommendations = financialAnalysisService.getRecommendations();

        assertThat(recommendations).anyMatch(r -> r.title().contains("Comida"));
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

    private static CategoryTotalProjection categoryTotal(Long id, String name, BigDecimal total) {
        return new CategoryTotalProjection() {
            @Override
            public Long getCategoryId() {
                return id;
            }

            @Override
            public String getCategoryName() {
                return name;
            }

            @Override
            public BigDecimal getTotal() {
                return total;
            }
        };
    }
}
