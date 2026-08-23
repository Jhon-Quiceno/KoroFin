package com.korofin.backend.service.scheduling;

import com.korofin.backend.entity.card.CreditCard;
import com.korofin.backend.repository.card.CreditCardRepository;
import com.korofin.backend.service.card.CycleCloseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Job diario que cierra el ciclo de facturación de toda {@link CreditCard} cuyo día de corte ya
 * llegó, delegando la materialización del interés a {@link CycleCloseService#closeCycle(Long)} —
 * espeja la estructura de {@link PaymentReminderJob} (escaneo cross-user, {@link Clock} inyectado,
 * no {@code @Transactional} en sí mismo).
 *
 * <p>A diferencia de un escaneo naíf "{@code cutoffDay == hoy}",
 * {@link CreditCardRepository#findCardsPendingCycleClose} devuelve toda tarjeta que todavía no
 * cerró hoy (conjunto de candidatas amplio), y este job después decide, por tarjeta, si su corte
 * ya llegó vía {@link #isCutoffDue}. Este diseño en dos pasos es lo que hace al job resiliente a
 * caídas: si el servidor estuvo caído justo el día de corte de una tarjeta, la siguiente corrida
 * igual la ve como candidata (su {@code lastCutoffDate} sigue desactualizado) e igual encuentra su
 * día de corte "vencido" (ya pasó este mes), así que el ciclo perdido se cierra en vez de perderse
 * para siempre.
 *
 * <p>Cada tarjeta se cierra independientemente dentro de su propio try/catch: una excepción al
 * cerrar una tarjeta (por ejemplo un problema de datos específico de esa tarjeta) se loguea y no
 * aborta el resto del lote.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CardCycleCloseJob {

    private static final Logger log = LoggerFactory.getLogger(CardCycleCloseJob.class);

    private final CreditCardRepository creditCardRepository;
    private final CycleCloseService cycleCloseService;
    private final Clock clock;

    public CardCycleCloseJob(
            CreditCardRepository creditCardRepository,
            CycleCloseService cycleCloseService,
            Clock clock
    ) {
        this.creditCardRepository = creditCardRepository;
        this.cycleCloseService = cycleCloseService;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.jobs.card-cycle-close.cron:0 0 6 * * *}")
    public void closeDueCycles() {
        LocalDate today = LocalDate.now(clock);

        var candidates = creditCardRepository.findCardsPendingCycleClose(today);
        log.debug("card_cycle_close_job_scan candidates={} today={}", candidates.size(), today);

        candidates.stream()
                .filter(card -> isCutoffDue(card, today))
                .forEach(card -> closeCardSafely(card.getId(), today));
    }

    /**
     * El corte de una tarjeta está "vencido" cuando la fecha de corte más reciente que no es
     * posterior a {@code today} (la de este mes, o la del mes pasado si la de este mes todavía no
     * llegó) sigue siendo más nueva que {@code lastCutoffDate} de la tarjeta — es decir, ese ciclo
     * concreto nunca se cerró.
     */
    private boolean isCutoffDue(CreditCard card, LocalDate today) {
        LocalDate effectiveCutoffDate = effectiveCutoffDate(card.getCutoffDay(), today);
        return card.getLastCutoffDate() == null || card.getLastCutoffDate().isBefore(effectiveCutoffDate);
    }

    /**
     * Resuelve la fecha de corte más reciente (recortada al último día de un mes más corto) que
     * cae en o antes de {@code today}: el corte de este mes si ya llegó, si no el del mes pasado.
     */
    private LocalDate effectiveCutoffDate(int cutoffDay, LocalDate today) {
        LocalDate thisMonthCutoff = clampToMonth(YearMonth.from(today), cutoffDay);
        if (!thisMonthCutoff.isAfter(today)) {
            return thisMonthCutoff;
        }
        return clampToMonth(YearMonth.from(today).minusMonths(1), cutoffDay);
    }

    private LocalDate clampToMonth(YearMonth month, int day) {
        int clampedDay = Math.min(day, month.lengthOfMonth());
        return month.atDay(clampedDay);
    }

    private void closeCardSafely(Long cardId, LocalDate today) {
        try {
            cycleCloseService.closeCycle(cardId);
        } catch (RuntimeException ex) {
            log.error("card_cycle_close_job_failed cardId={} today={}", cardId, today, ex);
        }
    }
}
