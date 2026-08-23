package com.korofin.backend.service.scheduling;

import com.korofin.backend.dto.analysis.MonthEndPredictionResponse;
import com.korofin.backend.entity.notification.NotificationType;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.income.IncomeRepository;
import com.korofin.backend.service.analysis.MonthEndPredictionService;
import com.korofin.backend.service.notification.channel.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

/**
 * Job diario que le avisa a cada usuario con actividad reciente su proyección de gasto de fin de
 * mes, calculada por {@link MonthEndPredictionService#predict(Long)} — pendiente de la fase 6
 * (dominio {@code scheduling}), que dejó explícitamente esta clase y su cron sin implementar
 * porque dependían del dominio {@code analysis} (ver docs/backend-plan.md secciones 2.10/2.12).
 *
 * <p>Espeja la estructura de {@link WeeklySummaryJob}: el universo de usuarios a notificar es la
 * unión de quienes registraron un gasto o un ingreso en el mes en curso hasta hoy (no todo usuario
 * registrado — un usuario inactivo este mes no tiene ritmo de gasto que proyectar), y cada usuario
 * se procesa independientemente dentro de su propio try/catch, para que una falla puntual (por
 * ejemplo un problema de datos de un usuario específico) no aborte el resto del lote.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonthEndPredictionJob {

    private static final Logger log = LoggerFactory.getLogger(MonthEndPredictionJob.class);

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final MonthEndPredictionService monthEndPredictionService;
    private final NotificationDispatcher notificationDispatcher;
    private final Clock clock;

    public MonthEndPredictionJob(
            ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository,
            MonthEndPredictionService monthEndPredictionService,
            NotificationDispatcher notificationDispatcher,
            Clock clock
    ) {
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.monthEndPredictionService = monthEndPredictionService;
        this.notificationDispatcher = notificationDispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.jobs.month-end-prediction.cron:0 30 8 * * *}")
    public void notifyPredictions() {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate start = currentMonth.atDay(1);

        Set<Long> activeUserIds = new HashSet<>(expenseRepository.findDistinctUserIdsByDateBetween(start, today));
        activeUserIds.addAll(incomeRepository.findDistinctUserIdsByDateBetween(start, today));

        log.debug("month_end_prediction_job_scan activeUsers={} today={}", activeUserIds.size(), today);
        activeUserIds.forEach(userId -> notifySafely(userId, today));
    }

    private void notifySafely(Long userId, LocalDate today) {
        try {
            MonthEndPredictionResponse prediction = monthEndPredictionService.predict(userId);
            String dedupeKey = "month-end-prediction:" + userId + ":" + prediction.periodYear() + "-" + prediction.periodMonth();
            String message = "Si seguís gastando a este ritmo, terminarías el mes con un gasto total cercano a $"
                    + NotificationMessageFormatter.formatAmount(prediction.projectedExpense())
                    + " (llevás $" + NotificationMessageFormatter.formatAmount(prediction.currentExpense())
                    + " hasta hoy, día " + prediction.daysElapsed() + " de " + prediction.daysInMonth() + ").";

            notificationDispatcher.dispatch(
                    userId, NotificationType.MONTH_END_PREDICTION,
                    "Proyección de gasto de fin de mes", message, dedupeKey
            );
        } catch (RuntimeException ex) {
            log.error("month_end_prediction_job_failed userId={} today={}", userId, today, ex);
        }
    }
}
