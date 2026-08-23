package com.korofin.backend.card.service;

import com.korofin.backend.card.entity.CardFranchise;
import com.korofin.backend.card.entity.CreditCard;
import com.korofin.backend.card.entity.Installment;
import com.korofin.backend.card.entity.InstallmentStatus;
import com.korofin.backend.card.exception.InstallmentAmountTooLowException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmortizationServiceTest {

    private final AmortizationService amortizationService = new AmortizationService();

    @Test
    void buildScheduleSplitsCapitalEvenlyAndSumsExactlyToTheAmount() {
        List<Installment> schedule = amortizationService.buildSchedule(
                new BigDecimal("300000"), 3, new BigDecimal("0.0200"),
                LocalDate.of(2026, 6, 1), card(15)
        );

        assertThat(schedule).hasSize(3);
        assertThat(schedule).allSatisfy(installment ->
                assertThat(installment.getCapitalAmount()).isEqualByComparingTo("100000"));
        assertThat(sumCapital(schedule)).isEqualByComparingTo("300000");
    }

    /**
     * El residuo del redondeo se acumula en la última cuota, de modo que la suma de capitales sea
     * exactamente el monto de la compra y no quede ni un centavo sin amortizar.
     */
    @Test
    void buildScheduleAddsTheRoundingRemainderToTheLastInstallment() {
        List<Installment> schedule = amortizationService.buildSchedule(
                new BigDecimal("100"), 3, BigDecimal.ZERO, LocalDate.of(2026, 6, 1), card(15)
        );

        assertThat(schedule.get(0).getCapitalAmount()).isEqualByComparingTo("33.33");
        assertThat(schedule.get(1).getCapitalAmount()).isEqualByComparingTo("33.33");
        assertThat(schedule.get(2).getCapitalAmount()).isEqualByComparingTo("33.34");
        assertThat(sumCapital(schedule)).isEqualByComparingTo("100");
    }

    @Test
    void buildScheduleComputesStrictlyDecreasingInterestOverThePurchaseOutstanding() {
        List<Installment> schedule = amortizationService.buildSchedule(
                new BigDecimal("300000"), 3, new BigDecimal("0.0200"),
                LocalDate.of(2026, 6, 1), card(15)
        );

        // Interés sobre el saldo pendiente de ESTA compra antes de restar el capital de la cuota:
        // 300000*2% = 6000, 200000*2% = 4000, 100000*2% = 2000.
        assertThat(schedule.get(0).getInterestAmount()).isEqualByComparingTo("6000.00");
        assertThat(schedule.get(1).getInterestAmount()).isEqualByComparingTo("4000.00");
        assertThat(schedule.get(2).getInterestAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void buildScheduleStartsEveryInstallmentPendingAndNumberedFromOne() {
        List<Installment> schedule = amortizationService.buildSchedule(
                new BigDecimal("120000"), 4, new BigDecimal("0.0100"),
                LocalDate.of(2026, 6, 1), card(15)
        );

        assertThat(schedule).extracting(Installment::getNumber).containsExactly(1, 2, 3, 4);
        assertThat(schedule).allSatisfy(installment ->
                assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.PENDING));
    }

    @Test
    void buildScheduleUsesTheCutoffDayOfTheSameMonthWhenItHasNotPassedYet() {
        List<Installment> schedule = amortizationService.buildSchedule(
                new BigDecimal("200000"), 2, BigDecimal.ZERO, LocalDate.of(2026, 6, 10), card(15)
        );

        assertThat(schedule.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(schedule.get(1).getDueDate()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void buildScheduleRollsToNextMonthWhenTheCutoffDayAlreadyPassed() {
        List<Installment> schedule = amortizationService.buildSchedule(
                new BigDecimal("200000"), 2, BigDecimal.ZERO, LocalDate.of(2026, 6, 20), card(15)
        );

        assertThat(schedule.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    /** Corte el 31 en un mes que no tiene 31 días: se ajusta al último día calendario del mes. */
    @Test
    void buildScheduleClampsTheCutoffDayToTheLastDayOfShorterMonths() {
        List<Installment> schedule = amortizationService.buildSchedule(
                new BigDecimal("200000"), 2, BigDecimal.ZERO, LocalDate.of(2026, 2, 10), card(31)
        );

        assertThat(schedule.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    /**
     * Resguardo anti-capital-negativo: sin él, una compra muy chica a muchas cuotas produciría
     * una última cuota con capital cero o negativo tras el redondeo.
     */
    @Test
    void buildScheduleRejectsAnAmountTooLowForTheRequestedInstallmentCount() {
        assertThatThrownBy(() -> amortizationService.buildSchedule(
                new BigDecimal("0.35"), 48, new BigDecimal("0.0200"), LocalDate.of(2026, 6, 1), card(15))
        ).isInstanceOf(InstallmentAmountTooLowException.class);
    }

    @Test
    void buildScheduleWithZeroRateProducesZeroInterestOnEveryInstallment() {
        List<Installment> schedule = amortizationService.buildSchedule(
                new BigDecimal("300000"), 3, BigDecimal.ZERO, LocalDate.of(2026, 6, 1), card(15)
        );

        assertThat(schedule).allSatisfy(installment ->
                assertThat(installment.getInterestAmount()).isEqualByComparingTo("0.00"));
    }

    private BigDecimal sumCapital(List<Installment> schedule) {
        return schedule.stream()
                .map(Installment::getCapitalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CreditCard card(int cutoffDay) {
        CreditCard card = new CreditCard();
        card.setId(1L);
        card.setName("Visa Oro");
        card.setFranchise(CardFranchise.VISA);
        card.setCreditLimit(new BigDecimal("5000000"));
        card.setCurrentBalance(BigDecimal.ZERO);
        card.setMonthlyRate(new BigDecimal("0.0200"));
        card.setCutoffDay(cutoffDay);
        card.setPaymentDueDay(5);
        return card;
    }
}
