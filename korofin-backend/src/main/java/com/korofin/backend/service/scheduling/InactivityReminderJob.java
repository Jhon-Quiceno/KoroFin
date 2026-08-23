package com.korofin.backend.service.scheduling;

import com.korofin.backend.entity.notification.NotificationType;
import com.korofin.backend.repository.common.UserLastActivityProjection;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.income.IncomeRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.service.notification.channel.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Job diario que recuerda a los usuarios que se loguearon al menos una vez pero no registraron
 * ingresos ni gastos en los últimos {@value #INACTIVITY_THRESHOLD_DAYS} días.
 *
 * <p>Los usuarios que nunca se loguearon ({@code lastLoginAt IS NULL}) quedan excluidos — no
 * tienen de qué estar "inactivos". La "última actividad" es la más reciente entre el gasto y el
 * ingreso más reciente del usuario, ambos obtenidos como una sola consulta agrupada por
 * repositorio ({@link ExpenseRepository#findLatestExpenseDatePerUser()},
 * {@link IncomeRepository#findLatestIncomeDatePerUser()}) en vez de una consulta por usuario, así
 * que el costo de este job no crece linealmente con la cantidad de usuarios elegibles.
 *
 * <p>La clave de dedupe incluye la fecha de última actividad (o {@code "never"}), así que un
 * usuario que registra un movimiento nuevo y después vuelve a quedar inactivo recibe un
 * recordatorio fresco para la nueva racha, en vez de quedar silenciado para siempre por el
 * primero.
 *
 * <p>No es {@code @Transactional} en sí mismo: cada escaneo de repositorio ya corre en su propia
 * transacción de solo lectura, y toda escritura ocurre dentro del límite transaccional propio de
 * {@link NotificationDispatcher#dispatch}. Envolver este método en
 * {@code @Transactional(readOnly = true)} sería engañoso — despacha escrituras.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InactivityReminderJob {

    private static final Logger log = LoggerFactory.getLogger(InactivityReminderJob.class);
    private static final int INACTIVITY_THRESHOLD_DAYS = 3;
    private static final String NEVER_ACTIVE_LABEL = "never";

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final Clock clock;

    public InactivityReminderJob(
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository,
            NotificationDispatcher notificationDispatcher,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.jobs.inactivity-reminder.cron:0 0 9 * * *}")
    public void remindInactiveUsers() {
        List<Long> loggedInUserIds = userRepository.findAllIdsWithLastLoginNotNull();
        if (loggedInUserIds.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate threshold = today.minusDays(INACTIVITY_THRESHOLD_DAYS);

        Map<Long, LocalDate> lastExpenseByUser = toMap(expenseRepository.findLatestExpenseDatePerUser());
        Map<Long, LocalDate> lastIncomeByUser = toMap(incomeRepository.findLatestIncomeDatePerUser());

        log.debug("inactivity_reminder_job_scan candidates={} threshold={}", loggedInUserIds.size(), threshold);
        for (Long userId : loggedInUserIds) {
            LocalDate lastActivity = latest(lastExpenseByUser.get(userId), lastIncomeByUser.get(userId));
            if (lastActivity != null && !lastActivity.isBefore(threshold)) {
                continue;
            }

            remindUser(userId, lastActivity);
        }
    }

    private void remindUser(Long userId, LocalDate lastActivity) {
        String title = "Te extrañamos en KoroFin";
        String message = "No has registrado ingresos ni gastos en los últimos días. "
                + "Vuelve a KoroFin para mantener tus finanzas al día.";
        String activityLabel = lastActivity != null ? lastActivity.toString() : NEVER_ACTIVE_LABEL;
        String dedupeKey = "inactivity:" + userId + ":" + activityLabel;

        notificationDispatcher.dispatch(userId, NotificationType.INACTIVITY_REMINDER, title, message, dedupeKey);
    }

    private static LocalDate latest(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private static Map<Long, LocalDate> toMap(List<UserLastActivityProjection> projections) {
        Map<Long, LocalDate> map = new HashMap<>();
        projections.forEach(projection -> map.put(projection.getUserId(), projection.getLastDate()));
        return map;
    }
}
