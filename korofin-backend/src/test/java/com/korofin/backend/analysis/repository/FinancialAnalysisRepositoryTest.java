package com.korofin.backend.analysis.repository;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.analysis.entity.FinancialAnalysis;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FinancialAnalysisRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private FinancialAnalysisRepository financialAnalysisRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUser_IdAndPeriodYearAndPeriodMonthReturnsTheMatchingSnapshot() {
        User owner = userRepository.saveAndFlush(newUser("analysis-owner@korofin.dev"));
        financialAnalysisRepository.saveAndFlush(newSnapshot(owner, 2026, 7));
        financialAnalysisRepository.saveAndFlush(newSnapshot(owner, 2026, 8));

        var found = financialAnalysisRepository.findByUser_IdAndPeriodYearAndPeriodMonth(owner.getId(), 2026, 7);

        assertThat(found).isPresent();
        assertThat(found.get().getPeriodMonth()).isEqualTo(7);
    }

    @Test
    void findByUser_IdAndPeriodYearAndPeriodMonthReturnsEmptyForAnotherUser() {
        User owner = userRepository.saveAndFlush(newUser("analysis-owner2@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("analysis-other2@korofin.dev"));
        financialAnalysisRepository.saveAndFlush(newSnapshot(owner, 2026, 9));

        var found = financialAnalysisRepository.findByUser_IdAndPeriodYearAndPeriodMonth(other.getId(), 2026, 9);

        assertThat(found).isEmpty();
    }

    private FinancialAnalysis newSnapshot(User owner, int year, int month) {
        FinancialAnalysis snapshot = new FinancialAnalysis();
        snapshot.setUser(owner);
        snapshot.setPeriodYear(year);
        snapshot.setPeriodMonth(month);
        snapshot.setTotalIncome(BigDecimal.valueOf(1000));
        snapshot.setTotalExpense(BigDecimal.valueOf(500));
        snapshot.setTotalSavings(BigDecimal.valueOf(500));
        snapshot.setSavingsRate(BigDecimal.valueOf(50));
        return snapshot;
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
