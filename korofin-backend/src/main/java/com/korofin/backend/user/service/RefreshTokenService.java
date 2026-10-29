package com.korofin.backend.user.service;

import com.korofin.backend.user.entity.RefreshToken;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.user.exception.InvalidRefreshTokenException;
import com.korofin.backend.user.repository.RefreshTokenRepository;
import com.korofin.backend.common.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Emite, rota y revoca refresh tokens. Solo el hash SHA-256 del token crudo se persiste (nunca el
 * JWT en texto plano) — comparar hashes evita que una fuga de la base de datos por sí sola
 * permita reconstruir tokens válidos.
 *
 * <p>Cada sesión de login tiene un {@code familyId} propio que se propaga a lo largo de toda su
 * cadena de rotaciones ({@link RefreshToken#getFamilyId()}). Si se presenta un token ya rotado
 * (revocado pero por lo demás válido), es indicio de que fue robado y usado después de que el
 * dueño legítimo ya lo rotó: se revoca toda la familia para forzar re-login tanto del atacante
 * como de la sesión legítima comprometida, en vez de responder solo con un 401 silencioso.
 *
 * <p>TODO(seguridad): el plan del backend (sección 3.2) también sugiere permitir listar/revocar
 * sesiones activas por dispositivo. No está implementado todavía.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtService jwtService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public String createForUser(User user, boolean rememberMe) {
        return createForUser(user, rememberMe, UUID.randomUUID());
    }

    /**
     * Crea un refresh token dentro de una familia existente (usado por {@link #rotate}) o de una
     * nueva (login inicial de una sesión).
     */
    private String createForUser(User user, boolean rememberMe, UUID familyId) {
        UUID tokenId = UUID.randomUUID();
        String rawToken = jwtService.generateRefreshToken(user, tokenId);
        Claims claims = jwtService.parseRefreshToken(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenId(tokenId);
        refreshToken.setFamilyId(familyId);
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setCreatedAt(Instant.now());
        refreshToken.setExpiresAt(claims.getExpiration().toInstant());
        refreshToken.setRememberMe(rememberMe);
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    /**
     * Revoca el token presentado y emite uno nuevo para el mismo usuario, propagando
     * {@link RefreshToken#isRememberMe()} y {@link RefreshToken#getFamilyId()} del token guardado.
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken stored = resolveActiveToken(rawToken);
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        boolean rememberMe = stored.isRememberMe();
        String newRefreshToken = createForUser(stored.getUser(), rememberMe, stored.getFamilyId());
        return new RotationResult(stored.getUser(), newRefreshToken, rememberMe);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        try {
            RefreshToken refreshToken = resolveActiveToken(rawToken);
            refreshToken.setRevokedAt(Instant.now());
            refreshTokenRepository.save(refreshToken);
        } catch (InvalidRefreshTokenException ignored) {
            // El logout debe seguir siendo idempotente.
        }
    }

    /**
     * Revoca todos los refresh tokens activos de {@code userId}, para que un cambio de contraseña
     * invalide otras sesiones en vez de dejar tokens robados/vigentes utilizables.
     */
    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now());
    }

    private RefreshToken resolveActiveToken(String rawToken) {
        Claims claims = jwtService.parseRefreshToken(rawToken);
        String tokenIdRaw = claims.getId();
        if (tokenIdRaw == null || tokenIdRaw.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token inválido");
        }

        UUID tokenId = UUID.fromString(tokenIdRaw);
        Optional<RefreshToken> optionalToken = refreshTokenRepository.findByTokenId(tokenId);
        RefreshToken refreshToken = optionalToken.orElseThrow(
                () -> new InvalidRefreshTokenException("Refresh token inválido")
        );

        if (refreshToken.getRevokedAt() != null) {
            // El token en sí es válido (firma correcta, no expirado) pero ya fue rotado: que lo
            // presenten de nuevo es indicio de robo/reuso, no un error transitorio del cliente. Se
            // corta toda la familia (sesión) para forzar re-login del atacante y del dueño
            // legítimo, en vez de devolver solo un 401 silencioso.
            refreshTokenRepository.revokeAllActiveForFamily(refreshToken.getFamilyId(), Instant.now());
            throw new InvalidRefreshTokenException("Refresh token reutilizado: se revocó toda la sesión");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token expirado");
        }

        String hashedRawToken = hashToken(rawToken);
        // MessageDigest.isEqual (no String.equals) para que la comparación sea constante en tiempo,
        // igual que en TelegramWebhookFilter - String.equals corta en el primer byte distinto.
        if (!MessageDigest.isEqual(
                hashedRawToken.getBytes(StandardCharsets.UTF_8),
                refreshToken.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidRefreshTokenException("Refresh token inválido");
        }

        // Defensa en profundidad: el JWT no puede forjarse sin la clave de firma, pero comparar
        // explícitamente el subject contra el dueño de la fila evita depender únicamente de eso -
        // por ejemplo si en el futuro se relajara la validación de firma o quedara una fila huérfana.
        String subject = claims.getSubject();
        if (subject == null || !subject.equals(String.valueOf(refreshToken.getUser().getId()))) {
            throw new InvalidRefreshTokenException("Refresh token inválido");
        }

        return refreshToken;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No se pudo inicializar el algoritmo de hash para refresh token", ex);
        }
    }

    public record RotationResult(
            User user,
            String refreshToken,
            boolean rememberMe
    ) {
    }
}
