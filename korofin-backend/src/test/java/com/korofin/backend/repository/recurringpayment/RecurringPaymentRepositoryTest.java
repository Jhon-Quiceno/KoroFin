package com.korofin.backend.repository.recurringpayment;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.entity.recurringpayment.RecurringFrequency;
import com.korofin.backend.entity.recurringpayment.RecurringPayment;
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
class RecurringPaymentRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private RecurringPaymentRepository recurringPaymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByIdAndUser_IdReturnsEmptyForAnotherUsersRecurringPayment() {
        User owner = userRepository.saveAndFlush(newUser("owner-rp@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("other-rp@korofin.dev"));
        RecurringPayment payment = recurringPaymentRepository.saveAndFlush(
                newRecurringPayment(owner, "Netflix", LocalDate.of(2026, 2, 1))
        );

        assertThat(recurringPaymentRepository.findByIdAndUser_Id(payment.getId(), owner.getId())).isPresent();
        assertThat(recurringPaymentRepository.findByIdAndUser_Id(payment.getId(), other.getId())).isEmpty();
    }

    @Test
    void findAllByUser_IdPaginatesOnlyTheOwnersPayments() {
        User owner = userRepository.saveAndFlush(newUser("list-rp@korofin.dev"));
        recurringPaymentRepository.saveAndFlush(newRecurringPayment(owner, "Netflix", LocalDate.of(2026, 2, 1)));
        recurringPaymentRepository.saveAndFlush(newRecurringPayment(owner, "Spotify", LocalDate.of(2026, 2, 5)));

        var page = recurringPaymentRepository.findAllByUser_Id(owner.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    /**
     * Guard atómico: solo la llamada cuyo {@code currentDate} todavía coincide con el valor
     * persistido gana el avance; una segunda llamada con el mismo {@code currentDate} (ya
     * obsoleto tras la primera) no actualiza nada.
     */
    @Test
    void advanceNextPaymentDateOnlyUpdatesWhenCurrentDateStillMatches() {
        User owner = userRepository.saveAndFlush(newUser("advance-rp@korofin.dev"));
        RecurringPayment payment = recurringPaymentRepository.saveAndFlush(
                newRecurringPayment(owner, "Netflix", LocalDate.of(2026, 2, 1))
        );

        int firstAttempt = recurringPaymentRepository.advanceNextPaymentDate(
                payment.getId(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)
        );
        int secondAttempt = recurringPaymentRepository.advanceNextPaymentDate(
                payment.getId(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)
        );

        assertThat(firstAttempt).isEqualTo(1);
        assertThat(secondAttempt).isZero();
        assertThat(recurringPaymentRepository.findById(payment.getId()).orElseThrow().getNextPaymentDate())
                .isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void findActiveByNextPaymentDateBetweenExcludesInactiveAndOutOfWindowPayments() {
        User owner = userRepository.saveAndFlush(newUser("scan-rp@korofin.dev"));
        RecurringPayment inWindow = newRecurringPayment(owner, "Netflix", LocalDate.of(2026, 6, 3));
        RecurringPayment outOfWindow = newRecurringPayment(owner, "Seguro", LocalDate.of(2026, 8, 1));
        RecurringPayment inactive = newRecurringPayment(owner, "Cancelado", LocalDate.of(2026, 6, 3));
        inactive.setActive(false);
        recurringPaymentRepository.saveAndFlush(inWindow);
        recurringPaymentRepository.saveAndFlush(outOfWindow);
        recurringPaymentRepository.saveAndFlush(inactive);

        List<RecurringPayment> result = recurringPaymentRepository.findActiveByNextPaymentDateBetween(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10)
        );

        assertThat(result).extracting(RecurringPayment::getName).containsExactly("Netflix");
    }

    private RecurringPayment newRecurringPayment(User owner, String name, LocalDate nextPaymentDate) {
        RecurringPayment payment = new RecurringPayment();
        payment.setUser(owner);
        payment.setName(name);
        payment.setAmount(new BigDecimal("35000"));
        payment.setFrequency(RecurringFrequency.MONTHLY);
        payment.setNextPaymentDate(nextPaymentDate);
        payment.setActive(true);
        return payment;
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
