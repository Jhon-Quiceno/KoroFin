package com.korofin.backend.service.card;

import com.korofin.backend.entity.card.CardMovement;
import com.korofin.backend.entity.card.CardMovementType;
import com.korofin.backend.entity.card.CreditCard;
import com.korofin.backend.entity.card.Installment;
import com.korofin.backend.entity.card.InstallmentStatus;
import com.korofin.backend.repository.card.CardMovementRepository;
import com.korofin.backend.repository.card.CreditCardRepository;
import com.korofin.backend.repository.card.InstallmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CycleCloseServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-15T06:00:00Z"), ZoneOffset.UTC);

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private InstallmentRepository installmentRepository;

    @Mock
    private CardMovementRepository cardMovementRepository;

    private CycleCloseService cycleCloseService;

    private void buildService() {
        cycleCloseService = new CycleCloseService(
                creditCardRepository, installmentRepository, cardMovementRepository, FIXED_CLOCK
        );
    }

    /**
     * Idempotencia: si el guard atómico devuelve 0 filas, el ciclo de hoy ya se cerró (segunda
     * corrida del job el mismo día, o dos instancias concurrentes sobre la misma tarjeta). El
     * método debe cortar ahí mismo, sin leer cuotas, sin crear interés y sin tocar el saldo — de
     * lo contrario el interés se cobraría dos veces.
     */
    @Test
    void closeCycleDoesNothingWhenTheIdempotencyGuardReportsTheCycleWasAlreadyClosed() {
        buildService();
        when(creditCardRepository.markCutoffClosed(9L, TODAY)).thenReturn(0);

        cycleCloseService.closeCycle(9L);

        verifyNoInteractions(installmentRepository);
        verifyNoInteractions(cardMovementRepository);
        verify(creditCardRepository, never()).incrementBalance(anyLong(), any());
        verify(creditCardRepository, never()).findById(anyLong());
    }

    /**
     * Ejecutar dos veces seguidas debe producir exactamente un movimiento de interés: la primera
     * corrida pasa el guard, la segunda lo encuentra ya cerrado. Es la misma garantía del test de
     * arriba, pero expresada como la secuencia real que ocurriría en producción.
     */
    @Test
    void closingTheSameCycleTwiceCreatesTheInterestMovementOnlyOnce() {
        buildService();
        when(creditCardRepository.markCutoffClosed(9L, TODAY)).thenReturn(1, 0);
        when(installmentRepository.findByPlan_Movement_Card_IdAndStatusAndDueDateLessThanEqual(
                9L, InstallmentStatus.PENDING, TODAY))
                .thenReturn(List.of(installment(1L, "5000.00")));
        when(creditCardRepository.findById(9L)).thenReturn(Optional.of(card(9L, "100000")));
        when(cardMovementRepository.save(any(CardMovement.class))).thenAnswer(i -> i.getArgument(0));

        cycleCloseService.closeCycle(9L);
        cycleCloseService.closeCycle(9L);

        verify(cardMovementRepository, org.mockito.Mockito.times(1)).save(any(CardMovement.class));
        verify(creditCardRepository, org.mockito.Mockito.times(1))
                .incrementBalance(9L, new BigDecimal("5000.00"));
    }

    /** Varias cuotas vencidas producen UN SOLO movimiento agregado con la suma de sus intereses. */
    @Test
    void closeCycleCreatesOneAggregatedInterestMovementWithTheSumOfEveryDueInstallment() {
        buildService();
        when(creditCardRepository.markCutoffClosed(9L, TODAY)).thenReturn(1);
        when(installmentRepository.findByPlan_Movement_Card_IdAndStatusAndDueDateLessThanEqual(
                9L, InstallmentStatus.PENDING, TODAY))
                .thenReturn(List.of(
                        installment(1L, "6000.00"),
                        installment(2L, "4000.00"),
                        installment(3L, "2500.50")
                ));
        when(creditCardRepository.findById(9L)).thenReturn(Optional.of(card(9L, "100000")));
        when(cardMovementRepository.save(any(CardMovement.class))).thenAnswer(i -> i.getArgument(0));

        cycleCloseService.closeCycle(9L);

        ArgumentCaptor<CardMovement> captor = ArgumentCaptor.forClass(CardMovement.class);
        verify(cardMovementRepository).save(captor.capture());
        CardMovement interestMovement = captor.getValue();
        assertThat(interestMovement.getType()).isEqualTo(CardMovementType.INTEREST);
        assertThat(interestMovement.getAmount()).isEqualByComparingTo("12500.50");
        assertThat(interestMovement.getDate()).isEqualTo(TODAY);
        assertThat(interestMovement.getCycleCloseDate()).isEqualTo(TODAY);
        // Sin guard de cupo: el interés se devengó y siempre se aplica.
        verify(creditCardRepository).incrementBalance(9L, new BigDecimal("12500.50"));
    }

    @Test
    void closeCycleMarksEveryDueInstallmentBilledAndLinksItToTheInterestMovement() {
        buildService();
        Installment first = installment(1L, "6000.00");
        Installment second = installment(2L, "4000.00");
        when(creditCardRepository.markCutoffClosed(9L, TODAY)).thenReturn(1);
        when(installmentRepository.findByPlan_Movement_Card_IdAndStatusAndDueDateLessThanEqual(
                9L, InstallmentStatus.PENDING, TODAY)).thenReturn(List.of(first, second));
        when(creditCardRepository.findById(9L)).thenReturn(Optional.of(card(9L, "100000")));
        when(cardMovementRepository.save(any(CardMovement.class))).thenAnswer(invocation -> {
            CardMovement saved = invocation.getArgument(0);
            saved.setId(55L);
            return saved;
        });

        cycleCloseService.closeCycle(9L);

        assertThat(first.getStatus()).isEqualTo(InstallmentStatus.BILLED);
        assertThat(second.getStatus()).isEqualTo(InstallmentStatus.BILLED);
        assertThat(first.getInterestMovement().getId()).isEqualTo(55L);
        assertThat(second.getInterestMovement()).isSameAs(first.getInterestMovement());
        verify(installmentRepository).saveAll(List.of(first, second));
    }

    /**
     * Catch-up: si el cierre no corrió el día exacto del corte, las cuotas siguen PENDING con
     * dueDate en el pasado. La consulta usa {@code dueDate <= hoy}, así que la próxima corrida las
     * factura igual sin perder el ciclo — no hace falta lógica de recuperación aparte.
     */
    @Test
    void closeCycleAlsoBillsInstallmentsThatFellDueBeforeTodayWhenTheJobWasDown() {
        buildService();
        Installment overdue = installment(1L, "3000.00");
        overdue.setDueDate(TODAY.minusDays(9));
        when(creditCardRepository.markCutoffClosed(9L, TODAY)).thenReturn(1);
        when(installmentRepository.findByPlan_Movement_Card_IdAndStatusAndDueDateLessThanEqual(
                9L, InstallmentStatus.PENDING, TODAY)).thenReturn(List.of(overdue));
        when(creditCardRepository.findById(9L)).thenReturn(Optional.of(card(9L, "100000")));
        when(cardMovementRepository.save(any(CardMovement.class))).thenAnswer(i -> i.getArgument(0));

        cycleCloseService.closeCycle(9L);

        // La consulta se hace con la fecha de hoy como tope, no con la fecha exacta del corte.
        verify(installmentRepository).findByPlan_Movement_Card_IdAndStatusAndDueDateLessThanEqual(
                9L, InstallmentStatus.PENDING, TODAY);
        assertThat(overdue.getStatus()).isEqualTo(InstallmentStatus.BILLED);
    }

    /**
     * El guard pasa (queda registrado que el ciclo se revisó hoy) pero no hay nada que facturar:
     * no se crea un movimiento de interés en cero ni se toca el saldo.
     */
    @Test
    void closeCycleCreatesNoInterestMovementWhenThereAreNoDueInstallments() {
        buildService();
        when(creditCardRepository.markCutoffClosed(9L, TODAY)).thenReturn(1);
        when(installmentRepository.findByPlan_Movement_Card_IdAndStatusAndDueDateLessThanEqual(
                9L, InstallmentStatus.PENDING, TODAY)).thenReturn(List.of());

        cycleCloseService.closeCycle(9L);

        verifyNoInteractions(cardMovementRepository);
        verify(creditCardRepository, never()).incrementBalance(anyLong(), any());
        verify(installmentRepository, never()).saveAll(any());
    }

    private Installment installment(Long id, String interest) {
        Installment installment = new Installment();
        installment.setId(id);
        installment.setNumber(id.intValue());
        installment.setCapitalAmount(new BigDecimal("100000"));
        installment.setInterestAmount(new BigDecimal(interest));
        installment.setDueDate(TODAY);
        installment.setStatus(InstallmentStatus.PENDING);
        return installment;
    }

    private CreditCard card(Long id, String balance) {
        CreditCard card = new CreditCard();
        card.setId(id);
        card.setName("Visa Oro");
        card.setCreditLimit(new BigDecimal("5000000"));
        card.setCurrentBalance(new BigDecimal(balance));
        card.setMonthlyRate(new BigDecimal("0.0200"));
        card.setCutoffDay(15);
        card.setPaymentDueDay(5);
        return card;
    }
}
