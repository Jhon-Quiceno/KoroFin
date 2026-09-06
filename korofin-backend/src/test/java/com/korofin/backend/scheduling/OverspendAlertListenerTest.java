package com.korofin.backend.scheduling;

import com.korofin.backend.notification.entity.NotificationType;
import com.korofin.backend.expense.event.ExpenseCreatedEvent;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.income.repository.IncomeRepository;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverspendAlertListenerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-10T08:00:00Z"), ZoneOffset.UTC
    );
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 7, 31);

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private OverspendAlertListener overspendAlertListener;

    @Test
    void dispatchesOverspendAlertWhenExpenseRatioExceedsEightyPercent() {
        overspendAlertListener = new OverspendAlertListener(
                incomeRepository, expenseRepository, notificationDispatcher, FIXED_CLOCK
        );
        when(incomeRepository.sumAmountByUserAndPeriod(1L, PERIOD_START, PERIOD_END)).thenReturn(BigDecimal.valueOf(1000));
        when(expenseRepository.sumAmountByUserAndPeriod(1L, PERIOD_START, PERIOD_END)).thenReturn(BigDecimal.valueOf(850));

        overspendAlertListener.onExpenseCreated(new ExpenseCreatedEvent(1L, 99L));

        verify(notificationDispatcher).dispatch(
                eq(1L), eq(NotificationType.OVERSPEND_ALERT), anyString(), anyString(), eq("overspend:1:2026-07")
        );
    }

    @Test
    void doesNotDispatchWhenRatioIsAtOrBelowEightyPercent() {
        overspendAlertListener = new OverspendAlertListener(
                incomeRepository, expenseRepository, notificationDispatcher, FIXED_CLOCK
        );
        when(incomeRepository.sumAmountByUserAndPeriod(2L, PERIOD_START, PERIOD_END)).thenReturn(BigDecimal.valueOf(1000));
        when(expenseRepository.sumAmountByUserAndPeriod(2L, PERIOD_START, PERIOD_END)).thenReturn(BigDecimal.valueOf(800));

        overspendAlertListener.onExpenseCreated(new ExpenseCreatedEvent(2L, 100L));

        verify(notificationDispatcher, never()).dispatch(
                eq(2L), eq(NotificationType.OVERSPEND_ALERT), anyString(), anyString(), anyString()
        );
    }

    @Test
    void doesNotDispatchWhenIncomeIsZero() {
        overspendAlertListener = new OverspendAlertListener(
                incomeRepository, expenseRepository, notificationDispatcher, FIXED_CLOCK
        );
        when(incomeRepository.sumAmountByUserAndPeriod(3L, PERIOD_START, PERIOD_END)).thenReturn(BigDecimal.ZERO);

        overspendAlertListener.onExpenseCreated(new ExpenseCreatedEvent(3L, 101L));

        verify(notificationDispatcher, never()).dispatch(
                eq(3L), eq(NotificationType.OVERSPEND_ALERT), anyString(), anyString(), anyString()
        );
    }
}
