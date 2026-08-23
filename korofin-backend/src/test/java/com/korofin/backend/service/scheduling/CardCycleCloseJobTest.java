package com.korofin.backend.service.scheduling;

import com.korofin.backend.entity.card.CreditCard;
import com.korofin.backend.repository.card.CreditCardRepository;
import com.korofin.backend.service.card.CycleCloseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardCycleCloseJobTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-15T06:00:00Z"), ZoneOffset.UTC
    );

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private CycleCloseService cycleCloseService;

    private CardCycleCloseJob cardCycleCloseJob;

    @Test
    void closesCardWhoseCutoffDayIsExactlyToday() {
        cardCycleCloseJob = new CardCycleCloseJob(creditCardRepository, cycleCloseService, FIXED_CLOCK);
        CreditCard card = buildCard(1L, 15, null);
        when(creditCardRepository.findCardsPendingCycleClose(LocalDate.of(2026, 7, 15)))
                .thenReturn(List.of(card));

        cardCycleCloseJob.closeDueCycles();

        verify(cycleCloseService).closeCycle(1L);
    }

    @Test
    void skipsCardWhoseCutoffDayHasNotArrivedThisMonthYet() {
        cardCycleCloseJob = new CardCycleCloseJob(creditCardRepository, cycleCloseService, FIXED_CLOCK);
        // cutoffDay=20 todavía no llegó (hoy es 15), y ya se cerró en junio (lastCutoffDate=20/06)
        CreditCard card = buildCard(2L, 20, LocalDate.of(2026, 6, 20));
        when(creditCardRepository.findCardsPendingCycleClose(LocalDate.of(2026, 7, 15)))
                .thenReturn(List.of(card));

        cardCycleCloseJob.closeDueCycles();

        verify(cycleCloseService, never()).closeCycle(2L);
    }

    @Test
    void catchesUpCardWhoseCutoffDayAlreadyPassedButWasNeverClosedDueToDowntime() {
        cardCycleCloseJob = new CardCycleCloseJob(creditCardRepository, cycleCloseService, FIXED_CLOCK);
        // cutoffDay=10 (ya pasó este mes); el último cierre registrado fue en mayo (downtime en
        // junio impidió el cierre de ese mes). El job de hoy (15/07) debe engancharla igual.
        CreditCard card = buildCard(3L, 10, LocalDate.of(2026, 5, 10));
        when(creditCardRepository.findCardsPendingCycleClose(LocalDate.of(2026, 7, 15)))
                .thenReturn(List.of(card));

        cardCycleCloseJob.closeDueCycles();

        verify(cycleCloseService).closeCycle(3L);
    }

    @Test
    void skipsCardAlreadyClosedForTheCurrentCycle() {
        cardCycleCloseJob = new CardCycleCloseJob(creditCardRepository, cycleCloseService, FIXED_CLOCK);
        // cutoffDay=10 ya pasó este mes y ya fue cerrado este mismo mes (lastCutoffDate=10/07).
        CreditCard card = buildCard(4L, 10, LocalDate.of(2026, 7, 10));
        when(creditCardRepository.findCardsPendingCycleClose(LocalDate.of(2026, 7, 15)))
                .thenReturn(List.of(card));

        cardCycleCloseJob.closeDueCycles();

        verify(cycleCloseService, never()).closeCycle(4L);
    }

    @Test
    void oneCardFailingDoesNotAbortTheRestOfTheBatch() {
        cardCycleCloseJob = new CardCycleCloseJob(creditCardRepository, cycleCloseService, FIXED_CLOCK);
        CreditCard failingCard = buildCard(5L, 15, null);
        CreditCard healthyCard = buildCard(6L, 15, null);
        when(creditCardRepository.findCardsPendingCycleClose(LocalDate.of(2026, 7, 15)))
                .thenReturn(List.of(failingCard, healthyCard));
        doThrow(new RuntimeException("boom")).when(cycleCloseService).closeCycle(5L);

        cardCycleCloseJob.closeDueCycles();

        verify(cycleCloseService).closeCycle(eq(5L));
        verify(cycleCloseService).closeCycle(eq(6L));
    }

    private CreditCard buildCard(Long id, int cutoffDay, LocalDate lastCutoffDate) {
        CreditCard card = new CreditCard();
        card.setId(id);
        card.setCutoffDay(cutoffDay);
        card.setLastCutoffDate(lastCutoffDate);
        return card;
    }
}
