package com.korofin.backend.service.scheduling;

import com.korofin.backend.entity.notification.NotificationType;
import com.korofin.backend.repository.common.UserLastActivityProjection;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.income.IncomeRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.service.notification.channel.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InactivityReminderJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T06:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private InactivityReminderJob job;

    private void buildJob() {
        job = new InactivityReminderJob(userRepository, expenseRepository, incomeRepository, notificationDispatcher, FIXED_CLOCK);
    }

    @Test
    void remindInactiveUsersDoesNothingWhenNoUserEverLoggedIn() {
        buildJob();
        when(userRepository.findAllIdsWithLastLoginNotNull()).thenReturn(List.of());

        job.remindInactiveUsers();

        verifyNoInteractions(expenseRepository);
        verifyNoInteractions(incomeRepository);
        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void remindInactiveUsersSkipsUsersWithRecentActivity() {
        buildJob();
        when(userRepository.findAllIdsWithLastLoginNotNull()).thenReturn(List.of(1L));
        when(expenseRepository.findLatestExpenseDatePerUser())
                .thenReturn(List.of(activity(1L, TODAY.minusDays(1))));
        when(incomeRepository.findLatestIncomeDatePerUser()).thenReturn(List.of());

        job.remindInactiveUsers();

        verify(notificationDispatcher, never()).dispatch(any(), any(), any(), any(), any());
    }

    @Test
    void remindInactiveUsersDispatchesForUsersPastTheThreshold() {
        buildJob();
        when(userRepository.findAllIdsWithLastLoginNotNull()).thenReturn(List.of(1L));
        when(expenseRepository.findLatestExpenseDatePerUser())
                .thenReturn(List.of(activity(1L, TODAY.minusDays(10))));
        when(incomeRepository.findLatestIncomeDatePerUser()).thenReturn(List.of());

        job.remindInactiveUsers();

        verify(notificationDispatcher).dispatch(
                eq(1L), eq(NotificationType.INACTIVITY_REMINDER), any(), any(),
                eq("inactivity:1:" + TODAY.minusDays(10))
        );
    }

    @Test
    void remindInactiveUsersUsesNeverLabelWhenUserHasNoRecordedActivity() {
        buildJob();
        when(userRepository.findAllIdsWithLastLoginNotNull()).thenReturn(List.of(2L));
        when(expenseRepository.findLatestExpenseDatePerUser()).thenReturn(List.of());
        when(incomeRepository.findLatestIncomeDatePerUser()).thenReturn(List.of());

        job.remindInactiveUsers();

        verify(notificationDispatcher).dispatch(
                eq(2L), eq(NotificationType.INACTIVITY_REMINDER), any(), any(), eq("inactivity:2:never")
        );
    }

    @Test
    void remindInactiveUsersUsesTheLatestOfExpenseAndIncomeActivity() {
        buildJob();
        when(userRepository.findAllIdsWithLastLoginNotNull()).thenReturn(List.of(3L));
        when(expenseRepository.findLatestExpenseDatePerUser())
                .thenReturn(List.of(activity(3L, TODAY.minusDays(10))));
        when(incomeRepository.findLatestIncomeDatePerUser())
                .thenReturn(List.of(activity(3L, TODAY.minusDays(1))));

        job.remindInactiveUsers();

        // El ingreso es mas reciente que el gasto y esta dentro del umbral: no se notifica.
        verify(notificationDispatcher, never()).dispatch(any(), any(), any(), any(), any());
    }

    private UserLastActivityProjection activity(Long userId, LocalDate lastDate) {
        return new UserLastActivityProjection() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public LocalDate getLastDate() {
                return lastDate;
            }
        };
    }
}
