package com.korofin.backend.scheduling;

import com.korofin.backend.notification.entity.NotificationType;
import com.korofin.backend.expense.event.ExpenseCreatedEvent;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.income.repository.IncomeRepository;
import com.korofin.backend.notification.service.channel.NotificationDispatcher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Reacciona a {@link ExpenseCreatedEvent} recalculando la razón gasto/ingreso del mes actual para
 * el dueño del gasto y despachando una alerta de sobregasto una vez que cruza el 80%.
 *
 * <p>Corre solo en {@link TransactionPhase#AFTER_COMMIT}: el gasto que dispara este evento se crea
 * dentro de la propia transacción de {@code ExpenseService#createExpense}, y correr el chequeo de
 * sobregasto (y su escritura de notificación) dentro de esa misma transacción dejaría que una
 * {@code DataIntegrityViolationException} de la carrera de dedupe de
 * {@code NotificationService#createNotification} envenene y revierta en silencio la transacción de
 * quien llama. Diferir a after-commit garantiza que el gasto ya quedó persistido de forma durable
 * antes de que este listener corra, así que ya no puede afectar el resultado del gasto. Abre su
 * propia transacción con {@link Propagation#REQUIRES_NEW} porque un listener
 * {@code AFTER_COMMIT} de otro modo se ejecuta fuera de cualquier contexto transaccional vivo, y
 * un simple {@code @Transactional} no arrancaría una nueva ahí.
 *
 * <p>Nunca depende de {@link com.korofin.backend.common.security.SecurityUtils}: el dueño se lee del
 * propio evento, así que este listener funciona sin importar si se dispara desde un hilo de
 * request o (en el futuro) cualquier contexto no-request.
 */
@Component
public class OverspendAlertListener {

    private static final BigDecimal OVERSPEND_THRESHOLD = new BigDecimal("0.80");
    private static final int RATIO_SCALE = 4;

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final Clock clock;

    public OverspendAlertListener(
            IncomeRepository incomeRepository,
            ExpenseRepository expenseRepository,
            NotificationDispatcher notificationDispatcher,
            Clock clock
    ) {
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onExpenseCreated(ExpenseCreatedEvent event) {
        YearMonth period = YearMonth.from(LocalDate.now(clock));
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();
        Long userId = event.userId();

        BigDecimal totalIncome = nullSafe(incomeRepository.sumAmountByUserAndPeriod(userId, periodStart, periodEnd));
        if (totalIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal totalExpense = nullSafe(expenseRepository.sumAmountByUserAndPeriod(userId, periodStart, periodEnd));
        BigDecimal ratio = totalExpense.divide(totalIncome, RATIO_SCALE, RoundingMode.HALF_UP);
        if (ratio.compareTo(OVERSPEND_THRESHOLD) <= 0) {
            return;
        }

        String title = "Alerta de sobregasto";
        String message = String.format(
                Locale.ROOT, "Ya gastaste el %.0f%% de tus ingresos este mes. Revisa tu presupuesto.",
                ratio.multiply(BigDecimal.valueOf(100))
        );
        String dedupeKey = String.format(Locale.ROOT, "overspend:%d:%d-%02d", userId, period.getYear(), period.getMonthValue());

        notificationDispatcher.dispatch(userId, NotificationType.OVERSPEND_ALERT, title, message, dedupeKey);
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
