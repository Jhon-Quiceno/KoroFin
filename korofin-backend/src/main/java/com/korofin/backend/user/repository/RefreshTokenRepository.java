package com.korofin.backend.user.repository;

import com.korofin.backend.user.entity.RefreshToken;
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

    /**
     * Revoca todos los tokens activos de una familia (cadena de rotación de una sesión). Se usa
     * cuando se detecta reuso de un token ya rotado, para cortarle el paso tanto al atacante como
     * a la sesión legítima comprometida.
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.familyId = :familyId AND r.revokedAt IS NULL")
    int revokeAllActiveForFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);
}
