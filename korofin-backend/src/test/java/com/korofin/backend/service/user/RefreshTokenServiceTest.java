package com.korofin.backend.service.user;

import com.korofin.backend.entity.user.RefreshToken;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.exception.user.InvalidRefreshTokenException;
import com.korofin.backend.repository.user.RefreshTokenRepository;
import com.korofin.backend.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void createForUserPersistsHashedTokenAndReturnsRawToken() {
        User user = new User();
        user.setId(1L);
        UUID tokenId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        when(jwtService.generateRefreshToken(eq(user), any(UUID.class))).thenReturn("raw-token");
        when(jwtService.parseRefreshToken("raw-token")).thenReturn(claims);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.createForUser(user, true);

        Assertions.assertEquals("raw-token", rawToken);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        Assertions.assertEquals(hashToken("raw-token"), captor.getValue().getTokenHash());
        Assertions.assertTrue(captor.getValue().isRememberMe());
        Assertions.assertSame(user, captor.getValue().getUser());
    }

    @Test
    void rotateShouldPropagateRememberMeTrueAndRevokeOldToken() {
        RotationTestResult result = rotateStoredToken(true);

        Assertions.assertTrue(result.result.rememberMe());
    }

    @Test
    void rotateShouldPropagateRememberMeFalseAndRevokeOldToken() {
        RotationTestResult result = rotateStoredToken(false);

        Assertions.assertFalse(result.result.rememberMe());
    }

    @Test
    void rotateShouldFailWhenStoredTokenIsAlreadyRevoked() {
        UUID tokenId = UUID.randomUUID();
        RefreshToken stored = new RefreshToken();
        stored.setTokenId(tokenId);
        stored.setRevokedAt(Instant.now().minusSeconds(10));

        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(tokenId.toString());
        when(jwtService.parseRefreshToken("revoked-token")).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(stored));

        Assertions.assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate("revoked-token"));
    }

    @Test
    void rotateShouldFailWhenStoredTokenIsExpired() {
        UUID tokenId = UUID.randomUUID();
        RefreshToken stored = new RefreshToken();
        stored.setTokenId(tokenId);
        stored.setExpiresAt(Instant.now().minusSeconds(10));

        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(tokenId.toString());
        when(jwtService.parseRefreshToken("expired-token")).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(stored));

        Assertions.assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate("expired-token"));
    }

    @Test
    void rotateShouldFailWhenTokenHashDoesNotMatchStoredHash() {
        UUID tokenId = UUID.randomUUID();
        RefreshToken stored = new RefreshToken();
        stored.setTokenId(tokenId);
        stored.setTokenHash("otro-hash-distinto");
        stored.setExpiresAt(Instant.now().plusSeconds(3600));

        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(tokenId.toString());
        when(jwtService.parseRefreshToken("tampered-token")).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(stored));

        Assertions.assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate("tampered-token"));
    }

    @Test
    void revokeShouldBeIdempotentWhenTokenIsAlreadyInvalid() {
        when(jwtService.parseRefreshToken("garbage"))
                .thenThrow(new InvalidRefreshTokenException("token inválido"));

        Assertions.assertDoesNotThrow(() -> refreshTokenService.revoke("garbage"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeShouldDoNothingWhenTokenIsBlank() {
        refreshTokenService.revoke(" ");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeAllForUserDelegatesToRepository() {
        refreshTokenService.revokeAllForUser(3L);

        verify(refreshTokenRepository).revokeAllActiveForUser(eq(3L), any(Instant.class));
    }

    private RotationTestResult rotateStoredToken(boolean rememberMe) {
        UUID tokenId = UUID.randomUUID();
        String oldRawToken = "old-refresh-token";
        User user = new User();
        user.setId(1L);

        RefreshToken stored = new RefreshToken();
        stored.setId(100L);
        stored.setTokenId(tokenId);
        stored.setUser(user);
        stored.setTokenHash(hashToken(oldRawToken));
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        stored.setRememberMe(rememberMe);

        Claims oldClaims = mock(Claims.class);
        when(oldClaims.getId()).thenReturn(tokenId.toString());
        when(jwtService.parseRefreshToken(oldRawToken)).thenReturn(oldClaims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(stored));

        String newRawToken = "new-refresh-token";
        when(jwtService.generateRefreshToken(eq(user), any(UUID.class))).thenReturn(newRawToken);
        Claims newClaims = mock(Claims.class);
        when(newClaims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(7200)));
        when(jwtService.parseRefreshToken(newRawToken)).thenReturn(newClaims);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(oldRawToken);

        Assertions.assertEquals(newRawToken, result.refreshToken());
        Assertions.assertSame(user, result.user());
        Assertions.assertNotNull(stored.getRevokedAt(), "el token presentado debe quedar revocado");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        RefreshToken savedOld = captor.getAllValues().get(0);
        RefreshToken savedNew = captor.getAllValues().get(1);
        Assertions.assertNotNull(savedOld.getRevokedAt());
        Assertions.assertEquals(rememberMe, savedNew.isRememberMe());

        return new RotationTestResult(result);
    }

    private record RotationTestResult(RefreshTokenService.RotationResult result) {
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
