package com.korofin.backend.repository.user;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.entity.user.RefreshToken;
import com.korofin.backend.entity.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RefreshTokenRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByTokenIdReturnsTheMatchingRow() {
        User user = persistUser("find@korofin.dev");
        UUID tokenId = UUID.randomUUID();
        entityManager.persistAndFlush(newRefreshToken(user, tokenId, false));

        assertThat(refreshTokenRepository.findByTokenId(tokenId)).isPresent();
        assertThat(refreshTokenRepository.findByTokenId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void revokeAllActiveForUserOnlyTouchesActiveTokensOfThatUser() {
        User owner = persistUser("owner@korofin.dev");
        User otherUser = persistUser("other@korofin.dev");

        RefreshToken active1 = entityManager.persistAndFlush(newRefreshToken(owner, UUID.randomUUID(), false));
        RefreshToken active2 = entityManager.persistAndFlush(newRefreshToken(owner, UUID.randomUUID(), false));
        RefreshToken alreadyRevoked = newRefreshToken(owner, UUID.randomUUID(), false);
        alreadyRevoked.setRevokedAt(Instant.now().minusSeconds(60));
        entityManager.persistAndFlush(alreadyRevoked);
        RefreshToken otherUsersToken = entityManager.persistAndFlush(newRefreshToken(otherUser, UUID.randomUUID(), false));

        int revokedCount = refreshTokenRepository.revokeAllActiveForUser(owner.getId(), Instant.now());
        entityManager.clear();

        assertThat(revokedCount).isEqualTo(2);
        assertThat(refreshTokenRepository.findById(active1.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshTokenRepository.findById(active2.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshTokenRepository.findById(otherUsersToken.getId()).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void deleteByExpiresAtBeforeRemovesOnlyExpiredTokens() {
        User user = persistUser("expired@korofin.dev");
        RefreshToken expired = newRefreshToken(user, UUID.randomUUID(), false);
        expired.setExpiresAt(Instant.now().minusSeconds(3600));
        RefreshToken active = newRefreshToken(user, UUID.randomUUID(), false);
        active.setExpiresAt(Instant.now().plusSeconds(3600));
        entityManager.persistAndFlush(expired);
        entityManager.persistAndFlush(active);

        refreshTokenRepository.deleteByExpiresAtBefore(Instant.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.findById(expired.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(active.getId())).isPresent();
    }

    @Test
    void tokenIdAndTokenHashUniqueConstraintsAreEnforcedByTheDatabase() {
        User user = persistUser("unique@korofin.dev");
        UUID tokenId = UUID.randomUUID();
        entityManager.persistAndFlush(newRefreshToken(user, tokenId, false));

        RefreshToken duplicate = newRefreshToken(user, tokenId, false);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> entityManager.persistAndFlush(duplicate));
    }

    private User persistUser(String email) {
        User user = new User();
        user.setName("Test");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setActive(true);
        return entityManager.persistAndFlush(user);
    }

    private RefreshToken newRefreshToken(User user, UUID tokenId, boolean rememberMe) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenId(tokenId);
        refreshToken.setUser(user);
        refreshToken.setTokenHash("hash-" + tokenId);
        refreshToken.setCreatedAt(Instant.now());
        refreshToken.setExpiresAt(Instant.now().plusSeconds(3600));
        refreshToken.setRememberMe(rememberMe);
        return refreshToken;
    }
}
