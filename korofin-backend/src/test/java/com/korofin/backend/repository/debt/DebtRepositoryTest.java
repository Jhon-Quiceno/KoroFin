package com.korofin.backend.repository.debt;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.entity.debt.Debt;
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
class DebtRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private DebtRepository debtRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByIdAndUser_IdReturnsEmptyForAnotherUsersDebt() {
        User owner = userRepository.saveAndFlush(newUser("owner-debt@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("other-debt@korofin.dev"));
        Debt debt = debtRepository.saveAndFlush(newDebt(owner, "Préstamo", "1000000", "1000000", null));

        assertThat(debtRepository.findByIdAndUser_Id(debt.getId(), owner.getId())).isPresent();
        assertThat(debtRepository.findByIdAndUser_Id(debt.getId(), other.getId())).isEmpty();
    }

    @Test
    void findAllByUser_IdOnlyReturnsDebtsOfThatUser() {
        User owner = userRepository.saveAndFlush(newUser("list-owner@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("list-other@korofin.dev"));
        debtRepository.saveAndFlush(newDebt(owner, "Mía", "100", "100", null));
        debtRepository.saveAndFlush(newDebt(other, "Ajena", "200", "200", null));

        assertThat(debtRepository.findAllByUser_Id(owner.getId(), PageRequest.of(0, 20)).getContent())
                .extracting(Debt::getName)
                .containsExactly("Mía");
    }

    @Test
    void decrementRemainingAmountAppliesTheDecrementWhenTheBalanceCoversIt() {
        User owner = userRepository.saveAndFlush(newUser("dec-ok@korofin.dev"));
        Debt debt = debtRepository.saveAndFlush(newDebt(owner, "Préstamo", "1000", "1000", null));

        int updatedRows = debtRepository.decrementRemainingAmount(debt.getId(), new BigDecimal("300"));

        assertThat(updatedRows).isEqualTo(1);
        assertThat(debtRepository.findById(debt.getId()).orElseThrow().getRemainingAmount())
                .isEqualByComparingTo("700");
    }

    /**
     * El guard vive en el {@code WHERE} del propio {@code UPDATE}: un abono mayor al saldo no
     * actualiza ninguna fila y deja el saldo intacto. Es lo que hace imposible que dos abonos
     * concurrentes validen ambos contra el mismo saldo obsoleto (carrera de "lost update").
     */
    @Test
    void decrementRemainingAmountRejectsAnAmountAboveTheBalanceAndLeavesItUntouched() {
        User owner = userRepository.saveAndFlush(newUser("dec-reject@korofin.dev"));
        Debt debt = debtRepository.saveAndFlush(newDebt(owner, "Préstamo", "1000", "200", null));

        int updatedRows = debtRepository.decrementRemainingAmount(debt.getId(), new BigDecimal("300"));

        assertThat(updatedRows).isZero();
        assertThat(debtRepository.findById(debt.getId()).orElseThrow().getRemainingAmount())
                .isEqualByComparingTo("200");
    }

    @Test
    void decrementRemainingAmountAllowsPayingTheExactRemainingBalance() {
        User owner = userRepository.saveAndFlush(newUser("dec-exact@korofin.dev"));
        Debt debt = debtRepository.saveAndFlush(newDebt(owner, "Préstamo", "1000", "200", null));

        assertThat(debtRepository.decrementRemainingAmount(debt.getId(), new BigDecimal("200"))).isEqualTo(1);
        assertThat(debtRepository.findById(debt.getId()).orElseThrow().getRemainingAmount())
                .isEqualByComparingTo("0");
    }

    /**
     * Dos decrementos secuenciales sobre el mismo saldo: el segundo ve el saldo ya reducido por el
     * primero, porque cada uno lee y escribe dentro de la misma sentencia SQL. Es la simulación
     * determinista de dos abonos concurrentes: el segundo se rechaza en vez de pisar al primero.
     */
    @Test
    void twoSequentialDecrementsCannotBothSucceedAgainstTheSameBalance() {
        User owner = userRepository.saveAndFlush(newUser("dec-race@korofin.dev"));
        Debt debt = debtRepository.saveAndFlush(newDebt(owner, "Préstamo", "1000", "500", null));

        assertThat(debtRepository.decrementRemainingAmount(debt.getId(), new BigDecimal("400"))).isEqualTo(1);
        assertThat(debtRepository.decrementRemainingAmount(debt.getId(), new BigDecimal("400"))).isZero();

        assertThat(debtRepository.findById(debt.getId()).orElseThrow().getRemainingAmount())
                .isEqualByComparingTo("100");
    }

    @Test
    void incrementRemainingAmountAddsToTheBalanceWithoutAnyCapGuard() {
        User owner = userRepository.saveAndFlush(newUser("inc-ok@korofin.dev"));
        Debt debt = debtRepository.saveAndFlush(newDebt(owner, "Préstamo", "1000", "1000", null));

        int updatedRows = debtRepository.incrementRemainingAmount(debt.getId(), new BigDecimal("2500"));

        assertThat(updatedRows).isEqualTo(1);
        // El saldo puede superar el total original: un cargo lo aumenta sin tope.
        assertThat(debtRepository.findById(debt.getId()).orElseThrow().getRemainingAmount())
                .isEqualByComparingTo("3500");
    }

    @Test
    void incrementRemainingAmountReportsZeroRowsForADebtThatNoLongerExists() {
        assertThat(debtRepository.incrementRemainingAmount(-1L, new BigDecimal("100"))).isZero();
    }

    @Test
    void sumRemainingAmountByUserAddsUpOnlyThatUsersDebts() {
        User owner = userRepository.saveAndFlush(newUser("sum-owner@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("sum-other@korofin.dev"));
        debtRepository.saveAndFlush(newDebt(owner, "A", "1000", "600", null));
        debtRepository.saveAndFlush(newDebt(owner, "B", "500", "500", null));
        // Una deuda saldada aporta cero sin necesitar un filtro de "activa".
        debtRepository.saveAndFlush(newDebt(owner, "C", "300", "0", null));
        debtRepository.saveAndFlush(newDebt(other, "Ajena", "9999", "9999", null));

        assertThat(debtRepository.sumRemainingAmountByUser(owner.getId())).isEqualByComparingTo("1100");
    }

    @Test
    void sumRemainingAmountByUserReturnsZeroWhenTheUserHasNoDebts() {
        User owner = userRepository.saveAndFlush(newUser("sum-empty@korofin.dev"));

        assertThat(debtRepository.sumRemainingAmountByUser(owner.getId())).isEqualByComparingTo("0");
    }

    @Test
    void findWithBalanceByDueDateBetweenSkipsSettledDebtsAndDatesOutsideTheRange() {
        User owner = userRepository.saveAndFlush(newUser("due-owner@korofin.dev"));
        debtRepository.saveAndFlush(newDebt(owner, "Vence dentro", "1000", "600", LocalDate.of(2026, 7, 10)));
        debtRepository.saveAndFlush(newDebt(owner, "Saldada", "1000", "0", LocalDate.of(2026, 7, 11)));
        debtRepository.saveAndFlush(newDebt(owner, "Vence fuera", "1000", "600", LocalDate.of(2026, 9, 1)));

        List<Debt> result = debtRepository.findWithBalanceByDueDateBetween(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)
        );

        assertThat(result).extracting(Debt::getName).containsExactly("Vence dentro");
        // JOIN FETCH d.user: el usuario viene cargado, sin lazy-load por fila.
        assertThat(result.get(0).getUser().getEmail()).isEqualTo("due-owner@korofin.dev");
    }

    private Debt newDebt(User owner, String name, String total, String remaining, LocalDate dueDate) {
        Debt debt = new Debt();
        debt.setUser(owner);
        debt.setName(name);
        debt.setTotalAmount(new BigDecimal(total));
        debt.setRemainingAmount(new BigDecimal(remaining));
        debt.setDueDate(dueDate);
        return debt;
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
