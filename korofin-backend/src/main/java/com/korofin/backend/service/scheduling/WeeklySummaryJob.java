package com.korofin.backend.service.scheduling;

import com.korofin.backend.entity.notification.NotificationType;
import com.korofin.backend.repository.expense.CategoryTotalProjection;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.income.IncomeRepository;
import com.korofin.backend.service.notification.channel.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Job semanal (default lunes 07:00) que le envía a todo usuario con alguna actividad en la semana
 * calendario previa un resumen de balance — ingresos vs. gastos, categoría con mayor gasto y
 * ahorro neto.
 *
 * <p>La ventana es {@code [hoy - 7, hoy - 1]} (los 7 días antes de la fecha de corrida), así que
 * una corrida en lunes resume la semana lunes-a-domingo recién completada. El conjunto de usuarios
 * a resumir se arma con dos escaneos baratos de ids distintos
 * ({@link IncomeRepository#findDistinctUserIdsByDateBetween} y
 * {@link ExpenseRepository#findDistinctUserIdsByDateBetween}) en vez de iterar sobre todo usuario
 * registrado, así que un usuario inactivo no cuesta nada acá.
 *
 * <p>No es {@code @Transactional} en sí mismo: cada escaneo de repositorio ya corre en su propia
 * transacción de solo lectura, y toda escritura ocurre dentro del límite transaccional propio de
 * {@link NotificationDispatcher#dispatch}. Envolver este método en
 * {@code @Transactional(readOnly = true)} sería engañoso — despacha escrituras.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WeeklySummaryJob {

    private static final Logger log = LoggerFactory.getLogger(WeeklySummaryJob.class);
    private static final int WINDOW_DAYS = 7;

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final Clock clock;

    public WeeklySummaryJob(
            ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository,
            NotificationDispatcher notificationDispatcher,
            Clock clock
    ) {
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.jobs.weekly-summary.cron:0 0 7 * * MON}")
    public void sendWeeklySummaries() {
        LocalDate today = LocalDate.now(clock);
        LocalDate windowStart = today.minusDays(WINDOW_DAYS);
        LocalDate windowEnd = today.minusDays(1);

        Set<Long> activeUserIds = new LinkedHashSet<>();
        activeUserIds.addAll(incomeRepository.findDistinctUserIdsByDateBetween(windowStart, windowEnd));
        activeUserIds.addAll(expenseRepository.findDistinctUserIdsByDateBetween(windowStart, windowEnd));

        log.debug("weekly_summary_job_scan users={} window={}..{}", activeUserIds.size(), windowStart, windowEnd);
        activeUserIds.forEach(userId -> sendSummaryForUser(userId, windowStart, windowEnd));
    }

    private void sendSummaryForUser(Long userId, LocalDate windowStart, LocalDate windowEnd) {
        BigDecimal income = nullSafe(incomeRepository.sumAmountByUserAndPeriod(userId, windowStart, windowEnd));
        BigDecimal expense = nullSafe(expenseRepository.sumAmountByUserAndPeriod(userId, windowStart, windowEnd));
        BigDecimal savings = income.subtract(expense);

        List<CategoryTotalProjection> topCategories = expenseRepository.findTopCategoriesByUserAndPeriod(userId, windowStart, windowEnd);
        String topCategoryName = topCategories.isEmpty() ? null : topCategories.get(0).getCategoryName();

        String title = "Resumen semanal";
        String message = buildMessage(income, expense, savings, topCategoryName);
        String dedupeKey = buildDedupeKey(userId, windowEnd);

        notificationDispatcher.dispatch(userId, NotificationType.WEEKLY_SUMMARY, title, message, dedupeKey);
    }

    private String buildMessage(BigDecimal income, BigDecimal expense, BigDecimal savings, String topCategoryName) {
        StringBuilder message = new StringBuilder("Esta semana: ingresos $")
                .append(NotificationMessageFormatter.formatAmount(income))
                .append(", gastos $")
                .append(NotificationMessageFormatter.formatAmount(expense));

        if (topCategoryName != null) {
            message.append(" (mayor gasto en '").append(topCategoryName).append("')");
        }

        if (savings.compareTo(BigDecimal.ZERO) < 0) {
            message.append(". Gastaste $").append(NotificationMessageFormatter.formatAmount(savings.abs()))
                    .append(" más de lo que ingresaste.");
        } else {
            message.append(". Ahorraste $").append(NotificationMessageFormatter.formatAmount(savings)).append('.');
        }

        return message.toString();
    }

    /** {@code weekly-summary:{userId}:{año-iso}-W{semana-iso}}, una alerta por usuario por semana ISO. */
    private String buildDedupeKey(Long userId, LocalDate windowEnd) {
        WeekFields weekFields = WeekFields.ISO;
        int isoYear = windowEnd.get(weekFields.weekBasedYear());
        int isoWeek = windowEnd.get(weekFields.weekOfWeekBasedYear());
        return String.format(Locale.ROOT, "weekly-summary:%d:%d-W%02d", userId, isoYear, isoWeek);
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
