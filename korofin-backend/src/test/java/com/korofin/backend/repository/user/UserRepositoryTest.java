package com.korofin.backend.repository.user;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.entity.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void existsByEmailIgnoreCaseIsCaseInsensitive() {
        userRepository.save(newUser("ana@korofin.dev"));

        assertThat(userRepository.existsByEmailIgnoreCase("ANA@KOROFIN.DEV")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("otro@korofin.dev")).isFalse();
    }

    @Test
    void findByEmailIgnoreCaseIsCaseInsensitive() {
        User saved = userRepository.save(newUser("ana@korofin.dev"));

        assertThat(userRepository.findByEmailIgnoreCase("Ana@Korofin.Dev"))
                .isPresent()
                .get()
                .extracting(User::getId)
                .isEqualTo(saved.getId());
    }

    @Test
    void emailUniqueConstraintIsEnforcedByTheDatabase() {
        userRepository.saveAndFlush(newUser("dup@korofin.dev"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("dup@korofin.dev")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void defaultsAreAppliedByTheDatabaseOnInsert() {
        User saved = userRepository.saveAndFlush(newUser("defaults@korofin.dev"));
        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTheme().name()).isEqualTo("SYSTEM");
        assertThat(reloaded.getCurrency()).isEqualTo("COP");
        assertThat(reloaded.getLanguage().name()).isEqualTo("ES");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void reserveAiChatQuotaShouldResetCounterToOneOnANewPeriod() {
        User saved = userRepository.saveAndFlush(newUser("quota-new-period@korofin.dev"));

        int rows = userRepository.reserveAiChatQuota(saved.getId(), "2026-07", 5);
        entityManager.clear();

        assertThat(rows).isEqualTo(1);
        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAiChatUsed()).isEqualTo(1);
        assertThat(reloaded.getAiChatPeriod()).isEqualTo("2026-07");
    }

    @Test
    void reserveAiChatQuotaShouldIncrementWithinTheSamePeriodUntilTheLimit() {
        User saved = userRepository.saveAndFlush(newUser("quota-increment@korofin.dev"));
        userRepository.reserveAiChatQuota(saved.getId(), "2026-07", 2);
        entityManager.clear();

        int rows = userRepository.reserveAiChatQuota(saved.getId(), "2026-07", 2);
        entityManager.clear();

        assertThat(rows).isEqualTo(1);
        assertThat(userRepository.findById(saved.getId()).orElseThrow().getAiChatUsed()).isEqualTo(2);
    }

    @Test
    void reserveAiChatQuotaShouldRejectOnceTheLimitIsReached() {
        User saved = userRepository.saveAndFlush(newUser("quota-limit@korofin.dev"));
        userRepository.reserveAiChatQuota(saved.getId(), "2026-07", 1);
        entityManager.clear();

        int rows = userRepository.reserveAiChatQuota(saved.getId(), "2026-07", 1);
        entityManager.clear();

        assertThat(rows).isEqualTo(0);
        assertThat(userRepository.findById(saved.getId()).orElseThrow().getAiChatUsed()).isEqualTo(1);
    }

    @Test
    void releaseAiChatQuotaShouldDecrementByOneFlooredAtZero() {
        User saved = userRepository.saveAndFlush(newUser("quota-release@korofin.dev"));
        userRepository.reserveAiChatQuota(saved.getId(), "2026-07", 5);
        entityManager.clear();

        int rows = userRepository.releaseAiChatQuota(saved.getId(), "2026-07");
        entityManager.clear();

        assertThat(rows).isEqualTo(1);
        assertThat(userRepository.findById(saved.getId()).orElseThrow().getAiChatUsed()).isEqualTo(0);
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
