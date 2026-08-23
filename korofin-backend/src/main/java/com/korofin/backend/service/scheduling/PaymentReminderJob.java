package com.korofin.backend.service.scheduling;

import com.korofin.backend.entity.debt.Debt;
import com.korofin.backend.entity.notification.NotificationType;
import com.korofin.backend.entity.recurringpayment.RecurringPayment;
import com.korofin.backend.repository.debt.DebtRepository;
import com.korofin.backend.repository.recurringpayment.RecurringPaymentRepository;
import com.korofin.backend.service.notification.channel.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Job diario que recuerda a los usuarios sus próximos pagos — pagos recurrentes activos y deudas
 * cuya fecha de vencimiento cae dentro de los próximos {@value #REMINDER_WINDOW_DAYS} días.
 *
 * <p>Ambos escaneos ({@link RecurringPaymentRepository#findActiveByNextPaymentDateBetween} y
 * {@link DebtRepository#findWithBalanceByDueDateBetween}) corren una sola vez a través de todos
 * los usuarios en vez de una vez por usuario, así que el costo de este job no crece con la
 * cantidad de usuarios registrados, solo con la cantidad de pagos/deudas efectivamente próximos a
 * vencer.
 *
 * <p>La clave de dedupe incluye la fecha de vencimiento misma (no solo el id del target), así que
 * un recordatorio vuelve a dispararse si la fecha se reprograma después — ver
 * {@link NotificationDispatcher#dispatch}.
 *
 * <p>No es {@code @Transactional} en sí mismo: cada escaneo de repositorio ya corre en su propia
 * transacción de solo lectura (default de Spring Data JPA para métodos de consulta), y toda
 * escritura ocurre dentro del límite transaccional propio de
 * {@link NotificationDispatcher#dispatch}. Envolver este método en
 * {@code @Transactional(readOnly = true)} sería engañoso — despacha escrituras — y envolverlo en
 * una transacción de escritura no serviría de nada, ya que nada acá necesita que los escaneos y
 * los despachos compartan una sola transacción.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderJob.class);
    private static final int REMINDER_WINDOW_DAYS = 5;

    private final RecurringPaymentRepository recurringPaymentRepository;
    private final DebtRepository debtRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final Clock clock;

    public PaymentReminderJob(
            RecurringPaymentRepository recurringPaymentRepository,
            DebtRepository debtRepository,
            NotificationDispatcher notificationDispatcher,
            Clock clock
    ) {
        this.recurringPaymentRepository = recurringPaymentRepository;
        this.debtRepository = debtRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.jobs.payment-reminder.cron:0 0 8 * * *}")
    public void remindUpcomingPayments() {
        LocalDate today = LocalDate.now(clock);
        LocalDate windowEnd = today.plusDays(REMINDER_WINDOW_DAYS);

        var duePayments = recurringPaymentRepository.findActiveByNextPaymentDateBetween(today, windowEnd);
        log.debug("payment_reminder_job_scan recurringPayments={} window={}..{}", duePayments.size(), today, windowEnd);
        duePayments.forEach(this::remindForRecurringPayment);

        var dueDebts = debtRepository.findWithBalanceByDueDateBetween(today, windowEnd);
        log.debug("payment_reminder_job_scan debts={} window={}..{}", dueDebts.size(), today, windowEnd);
        dueDebts.forEach(this::remindForDebt);
    }

    private void remindForRecurringPayment(RecurringPayment payment) {
        String title = "Recordatorio de pago: " + payment.getName();
        String message = "Tu servicio '" + payment.getName() + "' por $"
                + NotificationMessageFormatter.formatAmount(payment.getAmount())
                + " vence el " + NotificationMessageFormatter.formatDate(payment.getNextPaymentDate()) + ".";
        String dedupeKey = "payment-reminder:recurring:" + payment.getId() + ":" + payment.getNextPaymentDate();

        notificationDispatcher.dispatch(
                payment.getUser().getId(), NotificationType.PAYMENT_REMINDER, title, message, dedupeKey
        );
    }

    private void remindForDebt(Debt debt) {
        String title = "Recordatorio de pago: " + debt.getName();
        String message = "Tu deuda '" + debt.getName() + "' por $"
                + NotificationMessageFormatter.formatAmount(debt.getRemainingAmount())
                + " vence el " + NotificationMessageFormatter.formatDate(debt.getDueDate()) + ".";
        String dedupeKey = "payment-reminder:debt:" + debt.getId() + ":" + debt.getDueDate();

        notificationDispatcher.dispatch(
                debt.getUser().getId(), NotificationType.PAYMENT_REMINDER, title, message, dedupeKey
        );
    }
}
