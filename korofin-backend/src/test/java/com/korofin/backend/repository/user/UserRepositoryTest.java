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

    private User newUser(String email) {
        User user = new User();
        user.setName("Test");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setActive(true);
        return user;
    }
}
