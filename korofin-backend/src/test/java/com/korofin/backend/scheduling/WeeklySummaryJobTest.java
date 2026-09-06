package com.korofin.backend.scheduling;

import com.korofin.backend.notification.entity.NotificationType;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.income.repository.IncomeRepository;
import com.korofin.backend.notification.service.channel.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklySummaryJobTest {

    // Lunes: la ventana resumida es [today-7, today-1].
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T06:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate WINDOW_START = TODAY.minusDays(7);
    private static final LocalDate WINDOW_END = TODAY.minusDays(1);

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private WeeklySummaryJob job;

    private void buildJob() {
        job = new WeeklySummaryJob(expenseRepository, incomeRepository, notificationDispatcher, FIXED_CLOCK);
    }

    @Test
    void sendWeeklySummariesSkipsUsersWithNoActivityInTheWindow() {
        buildJob();
        when(incomeRepository.findDistinctUserIdsByDateBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of());
        when(expenseRepository.findDistinctUserIdsByDateBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of());

        job.sendWeeklySummaries();

        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void sendWeeklySummariesDispatchesOnceEvenWhenUserHasBothIncomeAndExpense() {
        buildJob();
        when(incomeRepository.findDistinctUserIdsByDateBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of(1L));
        when(expenseRepository.findDistinctUserIdsByDateBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of(1L));
        when(incomeRepository.sumAmountByUserAndPeriod(1L, WINDOW_START, WINDOW_END)).thenReturn(new BigDecimal("2000000"));
        when(expenseRepository.sumAmountByUserAndPeriod(1L, WINDOW_START, WINDOW_END)).thenReturn(new BigDecimal("500000"));
        when(expenseRepository.findTopCategoriesByUserAndPeriod(1L, WINDOW_START, WINDOW_END)).thenReturn(List.of());

        job.sendWeeklySummaries();

        verify(notificationDispatcher, org.mockito.Mockito.times(1)).dispatch(
                eq(1L), eq(NotificationType.WEEKLY_SUMMARY), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void sendWeeklySummariesMentionsOverspendWhenExpensesExceedIncome() {
        buildJob();
        when(incomeRepository.findDistinctUserIdsByDateBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of(1L));
        when(expenseRepository.findDistinctUserIdsByDateBetween(WINDOW_START, WINDOW_END)).thenReturn(List.of());
        when(incomeRepository.sumAmountByUserAndPeriod(1L, WINDOW_START, WINDOW_END)).thenReturn(new BigDecimal("100000"));
        when(expenseRepository.sumAmountByUserAndPeriod(1L, WINDOW_START, WINDOW_END)).thenReturn(new BigDecimal("150000"));
        when(expenseRepository.findTopCategoriesByUserAndPeriod(1L, WINDOW_START, WINDOW_END)).thenReturn(List.of());

        job.sendWeeklySummaries();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationDispatcher).dispatch(
                eq(1L), eq(NotificationType.WEEKLY_SUMMARY), org.mockito.ArgumentMatchers.any(),
                messageCaptor.capture(), org.mockito.ArgumentMatchers.any()
        );
        assertThat(messageCaptor.getValue()).contains("más de lo que ingresaste");
    }
}
