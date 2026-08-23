package com.korofin.backend.scheduling;

import com.korofin.backend.analysis.dto.MonthEndPredictionResponse;
import com.korofin.backend.notification.entity.NotificationType;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.income.repository.IncomeRepository;
import com.korofin.backend.analysis.service.MonthEndPredictionService;
import com.korofin.backend.notification.service.channel.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthEndPredictionJobTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-15T08:30:00Z"), ZoneOffset.UTC);

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private MonthEndPredictionService monthEndPredictionService;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private MonthEndPredictionJob job;

    @Test
    void notifiesEveryUserWithActivityThisMonth() {
        job = new MonthEndPredictionJob(expenseRepository, incomeRepository, monthEndPredictionService, notificationDispatcher, FIXED_CLOCK);
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate today = LocalDate.of(2026, 7, 15);
        when(expenseRepository.findDistinctUserIdsByDateBetween(start, today)).thenReturn(List.of(1L));
        when(incomeRepository.findDistinctUserIdsByDateBetween(start, today)).thenReturn(List.of(2L));
        when(monthEndPredictionService.predict(1L)).thenReturn(prediction());
        when(monthEndPredictionService.predict(2L)).thenReturn(prediction());

        job.notifyPredictions();

        verify(notificationDispatcher).dispatch(eq(1L), eq(NotificationType.MONTH_END_PREDICTION), anyString(), anyString(), anyString());
        verify(notificationDispatcher).dispatch(eq(2L), eq(NotificationType.MONTH_END_PREDICTION), anyString(), anyString(), anyString());
    }

    @Test
    void skipsUsersWithNoActivityThisMonth() {
        job = new MonthEndPredictionJob(expenseRepository, incomeRepository, monthEndPredictionService, notificationDispatcher, FIXED_CLOCK);
        when(expenseRepository.findDistinctUserIdsByDateBetween(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)))
                .thenReturn(List.of());
        when(incomeRepository.findDistinctUserIdsByDateBetween(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)))
                .thenReturn(List.of());

        job.notifyPredictions();

        verify(notificationDispatcher, never()).dispatch(eq(1L), eq(NotificationType.MONTH_END_PREDICTION), anyString(), anyString(), anyString());
    }

    @Test
    void oneUserFailingDoesNotAbortTheRestOfTheBatch() {
        job = new MonthEndPredictionJob(expenseRepository, incomeRepository, monthEndPredictionService, notificationDispatcher, FIXED_CLOCK);
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate today = LocalDate.of(2026, 7, 15);
        when(expenseRepository.findDistinctUserIdsByDateBetween(start, today)).thenReturn(List.of(1L, 2L));
        when(incomeRepository.findDistinctUserIdsByDateBetween(start, today)).thenReturn(List.of());
        when(monthEndPredictionService.predict(1L)).thenThrow(new RuntimeException("boom"));
        when(monthEndPredictionService.predict(2L)).thenReturn(prediction());

        job.notifyPredictions();

        verify(notificationDispatcher).dispatch(eq(2L), eq(NotificationType.MONTH_END_PREDICTION), anyString(), anyString(), anyString());
    }

    @Test
    void notificationDispatchFailureForOneUserDoesNotAbortTheRestOfTheBatch() {
        job = new MonthEndPredictionJob(expenseRepository, incomeRepository, monthEndPredictionService, notificationDispatcher, FIXED_CLOCK);
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate today = LocalDate.of(2026, 7, 15);
        when(expenseRepository.findDistinctUserIdsByDateBetween(start, today)).thenReturn(List.of(1L, 2L));
        when(incomeRepository.findDistinctUserIdsByDateBetween(start, today)).thenReturn(List.of());
        when(monthEndPredictionService.predict(1L)).thenReturn(prediction());
        when(monthEndPredictionService.predict(2L)).thenReturn(prediction());
        doThrow(new RuntimeException("dispatch failed"))
                .when(notificationDispatcher).dispatch(eq(1L), eq(NotificationType.MONTH_END_PREDICTION), anyString(), anyString(), anyString());

        job.notifyPredictions();

        verify(notificationDispatcher).dispatch(eq(2L), eq(NotificationType.MONTH_END_PREDICTION), anyString(), anyString(), anyString());
    }

    private MonthEndPredictionResponse prediction() {
        return new MonthEndPredictionResponse(2026, 7, BigDecimal.valueOf(300), BigDecimal.valueOf(20), BigDecimal.valueOf(620), 15, 31);
    }
}
