package com.korofin.backend.repository.card;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.entity.card.CardFranchise;
import com.korofin.backend.entity.card.CreditCard;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CreditCardRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private CreditCardRepository creditCardRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByIdAndUser_IdReturnsEmptyForAnotherUsersCard() {
        User owner = userRepository.saveAndFlush(newUser("owner-card@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("other-card@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner, "5000000", "0", 15));

        assertThat(creditCardRepository.findByIdAndUser_Id(card.getId(), owner.getId())).isPresent();
        assertThat(creditCardRepository.findByIdAndUser_Id(card.getId(), other.getId())).isEmpty();
    }

    @Test
    void findAllByUser_IdOnlyReturnsCardsOfThatUser() {
        User owner = userRepository.saveAndFlush(newUser("cards-owner@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("cards-other@korofin.dev"));
        creditCardRepository.saveAndFlush(newCard(owner, "5000000", "0", 15));
        creditCardRepository.saveAndFlush(newCard(other, "1000000", "0", 10));

        assertThat(creditCardRepository.findAllByUser_Id(owner.getId(), PageRequest.of(0, 20)))
                .hasSize(1);
    }

    @Test
    void incrementBalanceWithinLimitAppliesWhenThePurchaseFitsInTheAvailableCredit() {
        User owner = userRepository.saveAndFlush(newUser("inc-limit-ok@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner, "1000", "200", 15));

        int updatedRows = creditCardRepository.incrementBalanceWithinLimit(card.getId(), new BigDecimal("800"));

        assertThat(updatedRows).isEqualTo(1);
        assertThat(creditCardRepository.findById(card.getId()).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo("1000");
    }

    /**
     * El guard de cupo vive en el {@code WHERE} del propio {@code UPDATE}: una compra que no cabe
     * no actualiza ninguna fila y deja el saldo intacto, sin importar cuántas compras concurrentes
     * lo estén intentando a la vez.
     */
    @Test
    void incrementBalanceWithinLimitRejectsAPurchaseThatWouldExceedTheCreditLimit() {
        User owner = userRepository.saveAndFlush(newUser("inc-limit-reject@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner, "1000", "200", 15));

        int updatedRows = creditCardRepository.incrementBalanceWithinLimit(card.getId(), new BigDecimal("801"));

        assertThat(updatedRows).isZero();
        assertThat(creditCardRepository.findById(card.getId()).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo("200");
    }

    @Test
    void twoSequentialPurchasesCannotBothConsumeTheSameAvailableCredit() {
        User owner = userRepository.saveAndFlush(newUser("inc-limit-race@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner, "1000", "0", 15));

        assertThat(creditCardRepository.incrementBalanceWithinLimit(card.getId(), new BigDecimal("700")))
                .isEqualTo(1);
        assertThat(creditCardRepository.incrementBalanceWithinLimit(card.getId(), new BigDecimal("700")))
                .isZero();

        assertThat(creditCardRepository.findById(card.getId()).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo("700");
    }

    /** El interés del cierre de ciclo se aplica siempre, aunque deje la tarjeta sobre el cupo. */
    @Test
    void incrementBalanceAppliesEvenAboveTheCreditLimit() {
        User owner = userRepository.saveAndFlush(newUser("inc-nolimit@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner, "1000", "990", 15));

        assertThat(creditCardRepository.incrementBalance(card.getId(), new BigDecimal("50"))).isEqualTo(1);
        assertThat(creditCardRepository.findById(card.getId()).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo("1040");
    }

    @Test
    void decrementBalanceRejectsAPaymentAboveTheCurrentBalance() {
        User owner = userRepository.saveAndFlush(newUser("dec-card@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner, "1000", "300", 15));

        assertThat(creditCardRepository.decrementBalance(card.getId(), new BigDecimal("301"))).isZero();
        assertThat(creditCardRepository.decrementBalance(card.getId(), new BigDecimal("300"))).isEqualTo(1);
        assertThat(creditCardRepository.findById(card.getId()).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo("0");
    }

    /**
     * Guard de idempotencia del cierre de ciclo: la primera llamada marca la tarjeta como cerrada
     * hasta esa fecha, la segunda con la misma fecha devuelve 0 filas. Es lo que evita que un
     * segundo disparo del cierre el mismo día vuelva a cobrar el interés.
     */
    @Test
    void markCutoffClosedSucceedsOnceAndReportsZeroRowsOnTheSecondCallForTheSameDate() {
        User owner = userRepository.saveAndFlush(newUser("cutoff-idem@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner, "1000", "0", 15));
        LocalDate closeDate = LocalDate.of(2026, 6, 15);

        assertThat(creditCardRepository.markCutoffClosed(card.getId(), closeDate)).isEqualTo(1);
        assertThat(creditCardRepository.markCutoffClosed(card.getId(), closeDate)).isZero();

        assertThat(creditCardRepository.findById(card.getId()).orElseThrow().getLastCutoffDate())
                .isEqualTo(closeDate);
    }

    @Test
    void markCutoffClosedRejectsAnEarlierDateThanTheLastClosedCycle() {
        User owner = userRepository.saveAndFlush(newUser("cutoff-earlier@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner, "1000", "0", 15));

        assertThat(creditCardRepository.markCutoffClosed(card.getId(), LocalDate.of(2026, 6, 15))).isEqualTo(1);
        assertThat(creditCardRepository.markCutoffClosed(card.getId(), LocalDate.of(2026, 5, 15))).isZero();

        assertThat(creditCardRepository.findById(card.getId()).orElseThrow().getLastCutoffDate())
                .isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void markCutoffClosedAcceptsALaterDateForTheNextCycle() {
        User owner = userRepository.saveAndFlush(newUser("cutoff-next@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner, "1000", "0", 15));

        assertThat(creditCardRepository.markCutoffClosed(card.getId(), LocalDate.of(2026, 6, 15))).isEqualTo(1);
        assertThat(creditCardRepository.markCutoffClosed(card.getId(), LocalDate.of(2026, 7, 15))).isEqualTo(1);

        assertThat(creditCardRepository.findById(card.getId()).orElseThrow().getLastCutoffDate())
                .isEqualTo(LocalDate.of(2026, 7, 15));
    }

    /**
     * El escaneo de candidatas no filtra por "el corte es hoy" a propósito: una tarjeta cuyo
     * cierre se perdió hace días sigue apareciendo, que es lo que permite el catch-up.
     */
    @Test
    void findCardsPendingCycleCloseIncludesNeverClosedCardsAndStaleOnes() {
        User owner = userRepository.saveAndFlush(newUser("pending-close@korofin.dev"));
        CreditCard neverClosed = creditCardRepository.saveAndFlush(newCard(owner, "1000", "0", 15));
        CreditCard stale = newCard(owner, "1000", "0", 15);
        stale.setLastCutoffDate(LocalDate.of(2026, 5, 15));
        stale = creditCardRepository.saveAndFlush(stale);
        CreditCard closedToday = newCard(owner, "1000", "0", 15);
        closedToday.setLastCutoffDate(LocalDate.of(2026, 6, 15));
        closedToday = creditCardRepository.saveAndFlush(closedToday);

        List<CreditCard> result = creditCardRepository.findCardsPendingCycleClose(LocalDate.of(2026, 6, 15));

        assertThat(result).extracting(CreditCard::getId)
                .contains(neverClosed.getId(), stale.getId())
                .doesNotContain(closedToday.getId());
    }

    @Test
    void sumCurrentBalanceByUserAddsUpOnlyThatUsersCards() {
        User owner = userRepository.saveAndFlush(newUser("sum-cards@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("sum-cards-other@korofin.dev"));
        creditCardRepository.saveAndFlush(newCard(owner, "5000000", "300000", 15));
        creditCardRepository.saveAndFlush(newCard(owner, "2000000", "150000", 10));
        creditCardRepository.saveAndFlush(newCard(other, "1000000", "999999", 5));

        assertThat(creditCardRepository.sumCurrentBalanceByUser(owner.getId()))
                .isEqualByComparingTo("450000");
    }

    private CreditCard newCard(User owner, String creditLimit, String balance, int cutoffDay) {
        CreditCard card = new CreditCard();
        card.setUser(owner);
        card.setName("Visa Oro");
        card.setBank("Bancolombia");
        card.setFranchise(CardFranchise.VISA);
        card.setCreditLimit(new BigDecimal(creditLimit));
        card.setCurrentBalance(new BigDecimal(balance));
        card.setMonthlyRate(new BigDecimal("0.0250"));
        card.setCutoffDay(cutoffDay);
        card.setPaymentDueDay(5);
        return card;
    }

    private User newUser(String email) {
        User user = new User();
        user.setName("Test");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setActive(true);
        return user;
    }
}
