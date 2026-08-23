package com.korofin.backend.debt.repository;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.debt.entity.Debt;
import com.korofin.backend.debt.entity.DebtCharge;
import com.korofin.backend.debt.entity.DebtPayment;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de los repositorios del ledger de deudas ({@link DebtPaymentRepository} y
 * {@link DebtChargeRepository}) más la FK {@code debt_payment_id} agregada a {@code expenses} por
 * {@code V3__add_debt_and_card_domain.sql}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DebtLedgerRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private DebtRepository debtRepository;

    @Autowired
    private DebtPaymentRepository debtPaymentRepository;

    @Autowired
    private DebtChargeRepository debtChargeRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void paymentsAreListedNewestFirstAndScopedToTheirDebt() {
        User owner = userRepository.saveAndFlush(newUser("pay-list@korofin.dev"));
        Debt debt = debtRepository.saveAndFlush(newDebt(owner, "Préstamo"));
        Debt otherDebt = debtRepository.saveAndFlush(newDebt(owner, "Otra"));
        debtPaymentRepository.saveAndFlush(newPayment(debt, "100", LocalDate.of(2026, 1, 10)));
        debtPaymentRepository.saveAndFlush(newPayment(debt, "200", LocalDate.of(2026, 3, 5)));
        debtPaymentRepository.saveAndFlush(newPayment(otherDebt, "999", LocalDate.of(2026, 4, 1)));

        var page = debtPaymentRepository.findAllByDebt_IdOrderByPaymentDateDescIdDesc(
                debt.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(DebtPayment::getAmount)
                .satisfiesExactly(
                        first -> assertThat(first).isEqualByComparingTo("200"),
                        second -> assertThat(second).isEqualByComparingTo("100")
                );
    }

    @Test
    void chargesAreListedNewestFirstAndScopedToTheirDebt() {
        User owner = userRepository.saveAndFlush(newUser("charge-list@korofin.dev"));
        Debt debt = debtRepository.saveAndFlush(newDebt(owner, "Préstamo"));
        debtChargeRepository.saveAndFlush(newCharge(debt, "50", LocalDate.of(2026, 1, 10)));
        debtChargeRepository.saveAndFlush(newCharge(debt, "80", LocalDate.of(2026, 2, 10)));

        var page = debtChargeRepository.findAllByDebt_IdOrderByChargeDateDescIdDesc(
                debt.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(DebtCharge::getAmount)
                .satisfiesExactly(
                        first -> assertThat(first).isEqualByComparingTo("80"),
                        second -> assertThat(second).isEqualByComparingTo("50")
                );
    }

    /**
     * La FK {@code debt_payment_id} de {@code expenses} vincula el gasto generado con el abono que
     * lo originó, y la migración la declara {@code ON DELETE SET NULL}: el gasto tiene que
     * sobrevivir al borrado del abono, solo perdiendo el vínculo.
     */
    @Test
    void expenseKeepsItsRowWhenTheLinkedDebtPaymentIsDeleted() {
        User owner = userRepository.saveAndFlush(newUser("fk-payment@korofin.dev"));
        Debt debt = debtRepository.saveAndFlush(newDebt(owner, "Préstamo"));
        DebtPayment payment = debtPaymentRepository.saveAndFlush(
                newPayment(debt, "100", LocalDate.of(2026, 1, 10)));

        Expense expense = new Expense();
        expense.setUser(owner);
        expense.setAmount(new BigDecimal("100"));
        expense.setDate(LocalDate.of(2026, 1, 10));
        expense.setPaymentMethod(PaymentMethodType.OTHER);
        expense.setDebtPayment(payment);
        Expense savedExpense = expenseRepository.saveAndFlush(expense);
        assertThat(savedExpense.getDebtPayment().getId()).isEqualTo(payment.getId());

        debtPaymentRepository.delete(payment);
        debtPaymentRepository.flush();

        // El ON DELETE SET NULL lo aplica la base de datos, así que hay que vaciar el contexto de
        // persistencia para que la relectura vuelva a la base y no al caché de primer nivel.
        entityManager.clear();
        Expense reloaded = expenseRepository.findById(savedExpense.getId()).orElseThrow();
        assertThat(reloaded.getDebtPayment()).isNull();
        assertThat(reloaded.getAmount()).isEqualByComparingTo("100");
    }

    private Debt newDebt(User owner, String name) {
        Debt debt = new Debt();
        debt.setUser(owner);
        debt.setName(name);
        debt.setTotalAmount(new BigDecimal("1000000"));
        debt.setRemainingAmount(new BigDecimal("1000000"));
        return debt;
    }

    private DebtPayment newPayment(Debt debt, String amount, LocalDate date) {
        DebtPayment payment = new DebtPayment();
        payment.setDebt(debt);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentDate(date);
        return payment;
    }

    private DebtCharge newCharge(Debt debt, String amount, LocalDate date) {
        DebtCharge charge = new DebtCharge();
        charge.setDebt(debt);
        charge.setAmount(new BigDecimal(amount));
        charge.setChargeDate(date);
        return charge;
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
