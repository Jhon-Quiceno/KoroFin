package com.korofin.backend.analysis.service;

import com.korofin.backend.analysis.dto.MonthEndPredictionResponse;
import com.korofin.backend.expense.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthEndPredictionServiceTest {

    private static final Long USER_ID = 1L;
    // Día 15 de julio de 2026 (31 días en el mes)
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ExpenseRepository expenseRepository;

    @Test
    void predictsExpenseByExtrapolatingTheDailyAverageToTheFullMonth() {
        MonthEndPredictionService service = new MonthEndPredictionService(expenseRepository, FIXED_CLOCK);
        when(expenseRepository.sumAmountByUserAndPeriod(USER_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)))
                .thenReturn(BigDecimal.valueOf(300));

        MonthEndPredictionResponse prediction = service.predict(USER_ID);

        assertThat(prediction.periodYear()).isEqualTo(2026);
        assertThat(prediction.periodMonth()).isEqualTo(7);
        assertThat(prediction.currentExpense()).isEqualByComparingTo("300");
        assertThat(prediction.daysElapsed()).isEqualTo(15);
        assertThat(prediction.daysInMonth()).isEqualTo(31);
        assertThat(prediction.averageDailyExpense()).isEqualByComparingTo("20.00");
        assertThat(prediction.projectedExpense()).isEqualByComparingTo("620.00");
    }

    @Test
    void predictsZeroWhenTheUserHasNoExpensesThisMonth() {
        MonthEndPredictionService service = new MonthEndPredictionService(expenseRepository, FIXED_CLOCK);
        when(expenseRepository.sumAmountByUserAndPeriod(USER_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)))
                .thenReturn(null);

        MonthEndPredictionResponse prediction = service.predict(USER_ID);

        assertThat(prediction.currentExpense()).isEqualByComparingTo("0");
        assertThat(prediction.averageDailyExpense()).isEqualByComparingTo("0");
        assertThat(prediction.projectedExpense()).isEqualByComparingTo("0");
    }
}
