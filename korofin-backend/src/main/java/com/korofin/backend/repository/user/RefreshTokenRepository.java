package com.korofin.backend.repository.user;

import com.korofin.backend.entity.user.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenId(UUID tokenId);

    void deleteByExpiresAtBefore(Instant dateTime);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllActiveForUser(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
