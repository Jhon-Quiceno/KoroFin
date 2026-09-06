package com.korofin.backend.card.repository;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.card.entity.CardFranchise;
import com.korofin.backend.card.entity.CardMovement;
import com.korofin.backend.card.entity.CardMovementType;
import com.korofin.backend.card.entity.CreditCard;
import com.korofin.backend.card.entity.Installment;
import com.korofin.backend.card.entity.InstallmentPlan;
import com.korofin.backend.card.entity.InstallmentStatus;
import com.korofin.backend.expense.entity.Expense;
import com.korofin.backend.expense.entity.PaymentMethodType;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de los repositorios del ledger de tarjetas ({@link CardMovementRepository},
 * {@link InstallmentPlanRepository}, {@link InstallmentRepository}) más la FK
 * {@code card_movement_id} agregada a {@code expenses} por
 * {@code V3__add_debt_and_card_domain.sql}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CardLedgerRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private CreditCardRepository creditCardRepository;

    @Autowired
    private CardMovementRepository cardMovementRepository;

    @Autowired
    private InstallmentPlanRepository installmentPlanRepository;

    @Autowired
    private InstallmentRepository installmentRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void movementsAreScopedToTheirCardAndOptionallyFilteredByType() {
        User owner = userRepository.saveAndFlush(newUser("mov-list@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner));
        CreditCard otherCard = creditCardRepository.saveAndFlush(newCard(owner));
        cardMovementRepository.saveAndFlush(newMovement(card, CardMovementType.PURCHASE, "100"));
        cardMovementRepository.saveAndFlush(newMovement(card, CardMovementType.PAYMENT, "40"));
        cardMovementRepository.saveAndFlush(newMovement(otherCard, CardMovementType.PURCHASE, "999"));

        assertThat(cardMovementRepository.findAllByCard_Id(card.getId(), PageRequest.of(0, 20)))
                .hasSize(2);
        assertThat(cardMovementRepository.findAllByCard_IdAndType(
                card.getId(), CardMovementType.PURCHASE, PageRequest.of(0, 20)))
                .singleElement()
                .satisfies(movement -> assertThat(movement.getAmount()).isEqualByComparingTo("100"));
    }

    @Test
    void findByMovement_IdResolvesThePlanOriginatedByAnInstallmentPurchase() {
        User owner = userRepository.saveAndFlush(newUser("plan-lookup@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner));
        CardMovement movement = cardMovementRepository.saveAndFlush(
                newMovement(card, CardMovementType.INSTALLMENT_PURCHASE, "300000"));
        InstallmentPlan plan = savePlan(movement, 3, List.of(
                installment(1, "100000", "6000", LocalDate.of(2026, 6, 15)),
                installment(2, "100000", "4000", LocalDate.of(2026, 7, 15)),
                installment(3, "100000", "2000", LocalDate.of(2026, 8, 15))
        ));

        assertThat(installmentPlanRepository.findByMovement_Id(movement.getId()))
                .isPresent()
                .get()
                .satisfies(found -> assertThat(found.getId()).isEqualTo(plan.getId()));
    }

    @Test
    void findAllByPlan_IdOrderByNumberReturnsTheScheduleInOrder() {
        User owner = userRepository.saveAndFlush(newUser("plan-order@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner));
        CardMovement movement = cardMovementRepository.saveAndFlush(
                newMovement(card, CardMovementType.INSTALLMENT_PURCHASE, "300000"));
        InstallmentPlan plan = savePlan(movement, 3, List.of(
                installment(3, "100000", "2000", LocalDate.of(2026, 8, 15)),
                installment(1, "100000", "6000", LocalDate.of(2026, 6, 15)),
                installment(2, "100000", "4000", LocalDate.of(2026, 7, 15))
        ));

        assertThat(installmentRepository.findAllByPlan_IdOrderByNumber(plan.getId()))
                .extracting(Installment::getNumber)
                .containsExactly(1, 2, 3);
    }

    /**
     * Consulta que alimenta el cierre de ciclo: solo cuotas {@code PENDING} con
     * {@code dueDate <= hoy}, de cualquier plan de la tarjeta. Es la misma consulta que resuelve
     * el catch-up, porque una cuota vencida hace días sigue cumpliendo el predicado.
     */
    @Test
    void findDueInstallmentsReturnsOnlyPendingOnesUpToTheGivenDateAcrossEveryPlanOfTheCard() {
        User owner = userRepository.saveAndFlush(newUser("due-inst@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner));
        CreditCard otherCard = creditCardRepository.saveAndFlush(newCard(owner));

        CardMovement firstPurchase = cardMovementRepository.saveAndFlush(
                newMovement(card, CardMovementType.INSTALLMENT_PURCHASE, "200000"));
        savePlan(firstPurchase, 2, List.of(
                // Vencida hace días: el catch-up la sigue viendo.
                installment(1, "100000", "4000", LocalDate.of(2026, 6, 5)),
                installment(2, "100000", "2000", LocalDate.of(2026, 7, 15))
        ));

        CardMovement secondPurchase = cardMovementRepository.saveAndFlush(
                newMovement(card, CardMovementType.INSTALLMENT_PURCHASE, "100000"));
        InstallmentPlan secondPlan = savePlan(secondPurchase, 2, List.of(
                installment(1, "50000", "1000", LocalDate.of(2026, 6, 15)),
                installment(2, "50000", "500", LocalDate.of(2026, 7, 15))
        ));
        // Una cuota ya facturada no debe volver a aparecer.
        Installment alreadyBilled = installmentRepository.findAllByPlan_IdOrderByNumber(secondPlan.getId()).get(1);
        alreadyBilled.setDueDate(LocalDate.of(2026, 6, 1));
        alreadyBilled.setStatus(InstallmentStatus.BILLED);
        installmentRepository.saveAndFlush(alreadyBilled);

        CardMovement otherCardPurchase = cardMovementRepository.saveAndFlush(
                newMovement(otherCard, CardMovementType.INSTALLMENT_PURCHASE, "100000"));
        savePlan(otherCardPurchase, 2, List.of(
                installment(1, "50000", "9999", LocalDate.of(2026, 6, 1)),
                installment(2, "50000", "9999", LocalDate.of(2026, 7, 1))
        ));

        List<Installment> due = installmentRepository
                .findByPlan_Movement_Card_IdAndStatusAndDueDateLessThanEqual(
                        card.getId(), InstallmentStatus.PENDING, LocalDate.of(2026, 6, 15));

        assertThat(due).extracting(Installment::getInterestAmount)
                .containsExactlyInAnyOrder(new BigDecimal("4000.00"), new BigDecimal("1000.00"));
    }

    /**
     * La FK {@code card_movement_id} de {@code expenses} es {@code ON DELETE SET NULL}: borrar el
     * movimiento no debe borrar el gasto que generó, solo desvincularlo.
     */
    @Test
    void expenseKeepsItsRowWhenTheLinkedCardMovementIsDeleted() {
        User owner = userRepository.saveAndFlush(newUser("fk-movement@korofin.dev"));
        CreditCard card = creditCardRepository.saveAndFlush(newCard(owner));
        CardMovement movement = cardMovementRepository.saveAndFlush(
                newMovement(card, CardMovementType.PURCHASE, "250000"));

        Expense expense = new Expense();
        expense.setUser(owner);
        expense.setAmount(new BigDecimal("250000"));
        expense.setDate(LocalDate.of(2026, 6, 10));
        expense.setPaymentMethod(PaymentMethodType.CREDIT_CARD);
        expense.setCardMovement(movement);
        Expense savedExpense = expenseRepository.saveAndFlush(expense);
        assertThat(savedExpense.getCardMovement().getId()).isEqualTo(movement.getId());

        cardMovementRepository.delete(movement);
        cardMovementRepository.flush();

        entityManager.clear();
        Expense reloaded = expenseRepository.findById(savedExpense.getId()).orElseThrow();
        assertThat(reloaded.getCardMovement()).isNull();
        assertThat(reloaded.getAmount()).isEqualByComparingTo("250000");
    }

    private InstallmentPlan savePlan(CardMovement movement, int count, List<Installment> installments) {
        InstallmentPlan plan = new InstallmentPlan();
        plan.setMovement(movement);
        plan.setInstallmentCount(count);
        plan.setRateAtPurchase(new BigDecimal("0.0200"));
        List<Installment> owned = new ArrayList<>(installments);
        owned.forEach(installment -> installment.setPlan(plan));
        plan.setInstallments(owned);
        return installmentPlanRepository.saveAndFlush(plan);
    }

    private Installment installment(int number, String capital, String interest, LocalDate dueDate) {
        Installment installment = new Installment();
        installment.setNumber(number);
        installment.setCapitalAmount(new BigDecimal(capital));
        installment.setInterestAmount(new BigDecimal(interest));
        installment.setDueDate(dueDate);
        installment.setStatus(InstallmentStatus.PENDING);
        return installment;
    }

    private CardMovement newMovement(CreditCard card, CardMovementType type, String amount) {
        CardMovement movement = new CardMovement();
        movement.setCard(card);
        movement.setType(type);
        movement.setAmount(new BigDecimal(amount));
        movement.setDate(LocalDate.of(2026, 6, 10));
        return movement;
    }

    private CreditCard newCard(User owner) {
        CreditCard card = new CreditCard();
        card.setUser(owner);
        card.setName("Visa Oro");
        card.setFranchise(CardFranchise.VISA);
        card.setCreditLimit(new BigDecimal("5000000"));
        card.setCurrentBalance(BigDecimal.ZERO);
        card.setMonthlyRate(new BigDecimal("0.0250"));
        card.setCutoffDay(15);
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
