package com.korofin.backend.service.card;

import com.korofin.backend.entity.card.CardMovement;
import com.korofin.backend.entity.card.CardMovementType;
import com.korofin.backend.entity.card.CreditCard;
import com.korofin.backend.entity.card.Installment;
import com.korofin.backend.entity.card.InstallmentStatus;
import com.korofin.backend.entity.notification.NotificationType;
import com.korofin.backend.exception.card.CreditCardNotFoundException;
import com.korofin.backend.repository.card.CardMovementRepository;
import com.korofin.backend.repository.card.CreditCardRepository;
import com.korofin.backend.repository.card.InstallmentRepository;
import com.korofin.backend.service.notification.channel.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Materializa, por tarjeta, el interés de las cuotas {@link InstallmentStatus#PENDING} que ya
 * vencieron al cierre de ciclo.
 *
 * <p>{@link #closeCycle(Long)} es el único punto de entrada, pensado para invocarse una vez por
 * tarjeta. El {@code @Scheduled CardCycleCloseJob} que lo dispara a diario llega en la fase de
 * scheduling; este servicio ya es invocable por sí solo.
 *
 * <p>Es idempotente por dos mecanismos distintos que trabajan juntos:
 * <ul>
 *   <li>Un guard atómico ({@link CreditCardRepository#markCutoffClosed}) evita duplicar el
 *       interés si el cierre se dispara dos veces el mismo día o si dos instancias concurrentes
 *       procesan la misma tarjeta.</li>
 *   <li>La misma consulta que resuelve las cuotas vencidas
 *       ({@link InstallmentRepository#findByPlan_Movement_Card_IdAndStatusAndDueDateLessThanEqual})
 *       resuelve también el "catch-up": si el cierre no corrió el día exacto del corte, la
 *       próxima corrida sigue viendo esas cuotas como {@code PENDING} con {@code dueDate} en el
 *       pasado y las factura igual, sin perder el ciclo.</li>
 * </ul>
 *
 * <p>Cuando el cierre efectivamente materializa interés (hay cuotas vencidas), notifica al dueño
 * de la tarjeta vía {@link NotificationDispatcher} con un {@code dedupeKey} que incluye
 * {@code cardId} y la fecha de cierre — así una segunda corrida el mismo día (que ya corta antes
 * del guard) nunca llega a notificar dos veces, y el propio guard de dedupe de
 * {@code NotificationService#createNotification} es una segunda red de seguridad si alguna vez se
 * relaja esa condición. Si el guard pasa pero no hay cuotas vencidas, no se notifica — no hay
 * nada nuevo que informarle al usuario.
 */
@Service
public class CycleCloseService {

    private static final Logger log = LoggerFactory.getLogger(CycleCloseService.class);

    private final CreditCardRepository creditCardRepository;
    private final InstallmentRepository installmentRepository;
    private final CardMovementRepository cardMovementRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final Clock clock;

    public CycleCloseService(
            CreditCardRepository creditCardRepository,
            InstallmentRepository installmentRepository,
            CardMovementRepository cardMovementRepository,
            NotificationDispatcher notificationDispatcher,
            Clock clock
    ) {
        this.creditCardRepository = creditCardRepository;
        this.installmentRepository = installmentRepository;
        this.cardMovementRepository = cardMovementRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.clock = clock;
    }

    /**
     * Cierra el ciclo de facturación de la tarjeta {@code cardId} a la fecha de hoy (según
     * {@link #clock}):
     * <ol>
     *   <li>Guard atómico de idempotencia ({@link CreditCardRepository#markCutoffClosed}): si
     *       devuelve {@code 0} filas, el ciclo ya fue cerrado hoy (o después) y el método retorna
     *       sin hacer nada más.</li>
     *   <li>Busca las cuotas {@code PENDING} de la tarjeta con {@code dueDate <= hoy}.</li>
     *   <li>Si hay alguna, suma sus {@code interestAmount} y crea UN SOLO {@link CardMovement}
     *       agregado de tipo {@link CardMovementType#INTEREST} por el total, sumándolo al saldo
     *       vía {@link CreditCardRepository#incrementBalance} (sin guard de cupo: el interés se
     *       devengó y siempre se aplica, aunque deje la tarjeta sobregirada).</li>
     *   <li>Marca esas cuotas como {@link InstallmentStatus#BILLED} y les asigna el movimiento de
     *       interés recién creado.</li>
     *   <li>Notifica al dueño de la tarjeta el cierre y el saldo resultante, vía
     *       {@link NotificationDispatcher}.</li>
     * </ol>
     *
     * <p>Si el guard pasa pero no hay cuotas vencidas (tarjeta sin compras a cuotas, o ninguna
     * vencida todavía), no se crea movimiento de interés ni se notifica — solo queda registrado
     * que el ciclo se revisó hoy.
     */
    @Transactional
    public void closeCycle(Long cardId) {
        LocalDate today = LocalDate.now(clock);

        int guardedRows = creditCardRepository.markCutoffClosed(cardId, today);
        if (guardedRows == 0) {
            log.debug("cycle_close_skipped_already_closed cardId={} today={}", cardId, today);
            return;
        }

        List<Installment> dueInstallments = installmentRepository
                .findByPlan_Movement_Card_IdAndStatusAndDueDateLessThanEqual(
                        cardId, InstallmentStatus.PENDING, today);

        if (dueInstallments.isEmpty()) {
            log.debug("cycle_close_no_pending_installments cardId={} today={}", cardId, today);
            return;
        }

        BigDecimal interestTotal = dueInstallments.stream()
                .map(Installment::getInterestAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CreditCard card = findCardById(cardId);

        CardMovement interestMovement = new CardMovement();
        interestMovement.setCard(card);
        interestMovement.setType(CardMovementType.INTEREST);
        interestMovement.setAmount(interestTotal);
        interestMovement.setDate(today);
        interestMovement.setDescription("Interés del cierre de ciclo");
        interestMovement.setCycleCloseDate(today);
        CardMovement savedInterestMovement = cardMovementRepository.save(interestMovement);

        creditCardRepository.incrementBalance(cardId, interestTotal);

        dueInstallments.forEach(installment -> {
            installment.setStatus(InstallmentStatus.BILLED);
            installment.setInterestMovement(savedInterestMovement);
        });
        installmentRepository.saveAll(dueInstallments);

        // Se relee la tarjeta: incrementBalance corrió con clearAutomatically=true, así que el
        // "card" de más arriba quedó desvinculado del contexto de persistencia y su
        // currentBalance en memoria está desactualizado respecto al saldo real post-interés.
        CreditCard cardAfterInterest = findCardById(cardId);
        dispatchCycleCloseNotification(cardAfterInterest, today);

        log.info("cycle_close_completed cardId={} closeDate={} interestTotal={} billedInstallments={}",
                cardId, today, interestTotal, dueInstallments.size());
    }

    private void dispatchCycleCloseNotification(CreditCard card, LocalDate closeDate) {
        String title = "Cierre de ciclo: " + card.getName();
        String message = "Tu tarjeta " + card.getName() + " cerró su ciclo, saldo actual $"
                + formatAmount(card.getCurrentBalance()) + ".";
        String dedupeKey = "card-cycle-close:" + card.getId() + ":" + closeDate;

        notificationDispatcher.dispatch(
                card.getUser().getId(), NotificationType.CARD_CYCLE_CLOSE, title, message, dedupeKey
        );
    }

    /**
     * Formatea {@code amount} como cifra entera con separador de miles "." (ej. {@code "24.500"}).
     * Duplicado deliberadamente en vez de reusar {@code NotificationMessageFormatter}: ese
     * formateador vive en {@code service/scheduling} (package-private a propósito) y este
     * servicio pertenece a {@code service/card}, un paquete distinto.
     */
    private static String formatAmount(BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        BigDecimal rounded = safeAmount.setScale(0, RoundingMode.HALF_UP);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        return format.format(rounded);
    }

    private CreditCard findCardById(Long cardId) {
        return creditCardRepository.findById(cardId)
                .orElseThrow(CreditCardNotFoundException::new);
    }
}
