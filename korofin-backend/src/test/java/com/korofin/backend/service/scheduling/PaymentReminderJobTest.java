package com.korofin.backend.service.scheduling;

import com.korofin.backend.entity.debt.Debt;
import com.korofin.backend.entity.notification.NotificationType;
import com.korofin.backend.entity.recurringpayment.RecurringFrequency;
import com.korofin.backend.entity.recurringpayment.RecurringPayment;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.repository.debt.DebtRepository;
import com.korofin.backend.repository.recurringpayment.RecurringPaymentRepository;
import com.korofin.backend.service.notification.channel.NotificationDispatcher;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReminderJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T06:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RecurringPaymentRepository recurringPaymentRepository;

    @Mock
    private DebtRepository debtRepository;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private PaymentReminderJob job;

    private void buildJob() {
        job = new PaymentReminderJob(recurringPaymentRepository, debtRepository, notificationDispatcher, FIXED_CLOCK);
    }

    @Test
    void remindUpcomingPaymentsScansTheNextFiveDayWindow() {
        buildJob();
        when(recurringPaymentRepository.findActiveByNextPaymentDateBetween(TODAY, TODAY.plusDays(5)))
                .thenReturn(List.of());
        when(debtRepository.findWithBalanceByDueDateBetween(TODAY, TODAY.plusDays(5)))
                .thenReturn(List.of());

        job.remindUpcomingPayments();

        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void remindUpcomingPaymentsDispatchesOneReminderPerDueRecurringPayment() {
        buildJob();
        User owner = new User();
        owner.setId(1L);
        RecurringPayment payment = new RecurringPayment();
        payment.setId(9L);
        payment.setUser(owner);
        payment.setName("Netflix");
        payment.setAmount(new BigDecimal("35000"));
        payment.setNextPaymentDate(TODAY.plusDays(2));

        when(recurringPaymentRepository.findActiveByNextPaymentDateBetween(TODAY, TODAY.plusDays(5)))
                .thenReturn(List.of(payment));
        when(debtRepository.findWithBalanceByDueDateBetween(TODAY, TODAY.plusDays(5))).thenReturn(List.of());

        job.remindUpcomingPayments();

        verify(notificationDispatcher).dispatch(
                eq(1L), eq(NotificationType.PAYMENT_REMINDER), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), eq("payment-reminder:recurring:9:" + TODAY.plusDays(2))
        );
    }

    @Test
    void remindUpcomingPaymentsDispatchesOneReminderPerDueDebt() {
        buildJob();
        User owner = new User();
        owner.setId(2L);
        Debt debt = new Debt();
        debt.setId(7L);
        debt.setUser(owner);
        debt.setName("Préstamo");
        debt.setRemainingAmount(new BigDecimal("500000"));
        debt.setDueDate(TODAY.plusDays(3));

        when(recurringPaymentRepository.findActiveByNextPaymentDateBetween(TODAY, TODAY.plusDays(5)))
                .thenReturn(List.of());
        when(debtRepository.findWithBalanceByDueDateBetween(TODAY, TODAY.plusDays(5))).thenReturn(List.of(debt));

        job.remindUpcomingPayments();

        verify(notificationDispatcher).dispatch(
                eq(2L), eq(NotificationType.PAYMENT_REMINDER), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), eq("payment-reminder:debt:7:" + TODAY.plusDays(3))
        );
    }
}
