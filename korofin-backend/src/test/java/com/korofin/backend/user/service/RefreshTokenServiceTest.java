package com.korofin.backend.user.service;

import com.korofin.backend.user.entity.RefreshToken;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.user.exception.InvalidRefreshTokenException;
import com.korofin.backend.user.repository.RefreshTokenRepository;
import com.korofin.backend.common.security.JwtService;
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

    @Test
    void createForUserGeneratesADifferentFamilyIdPerSession() {
        User user = new User();
        user.setId(1L);
        Claims claims = mock(Claims.class);
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        when(jwtService.generateRefreshToken(eq(user), any(UUID.class)))
                .thenReturn("raw-token-1", "raw-token-2");
        when(jwtService.parseRefreshToken("raw-token-1")).thenReturn(claims);
        when(jwtService.parseRefreshToken("raw-token-2")).thenReturn(claims);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        refreshTokenService.createForUser(user, false);
        refreshTokenService.createForUser(user, false);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        UUID firstFamilyId = captor.getAllValues().get(0).getFamilyId();
        UUID secondFamilyId = captor.getAllValues().get(1).getFamilyId();

        Assertions.assertNotNull(firstFamilyId);
        Assertions.assertNotNull(secondFamilyId);
        Assertions.assertNotEquals(firstFamilyId, secondFamilyId,
                "dos logins del mismo usuario deben generar familias distintas");
    }

    @Test
    void rotateShouldPropagateFamilyIdFromOldTokenToNewOne() {
        UUID familyId = UUID.randomUUID();
        RotationTestResult result = rotateStoredToken(true, familyId);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        RefreshToken savedNew = captor.getAllValues().get(1);
        Assertions.assertEquals(familyId, savedNew.getFamilyId());
    }

    @Test
    void reusingAnAlreadyRotatedTokenRevokesTheWholeFamilyAndFails() {
        UUID familyId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        RefreshToken reusedToken = new RefreshToken();
        reusedToken.setTokenId(tokenId);
        reusedToken.setFamilyId(familyId);
        reusedToken.setRevokedAt(Instant.now().minusSeconds(30));

        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(tokenId.toString());
        when(jwtService.parseRefreshToken("reused-token")).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(reusedToken));

        Assertions.assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate("reused-token"));

        verify(refreshTokenRepository).revokeAllActiveForFamily(eq(familyId), any(Instant.class));
    }

    @Test
    void afterReuseIsDetectedNeitherTheOldNorTheCurrentFamilyTokenCanRotateAgain() {
        UUID familyId = UUID.randomUUID();

        // El token viejo ya presentado como reuso: sigue revocado, volver a intentarlo no debe
        // servir (y vuelve a disparar la revocación de familia, que es inofensiva/idempotente).
        UUID oldTokenId = UUID.randomUUID();
        RefreshToken oldToken = new RefreshToken();
        oldToken.setTokenId(oldTokenId);
        oldToken.setFamilyId(familyId);
        oldToken.setRevokedAt(Instant.now().minusSeconds(30));

        Claims oldClaims = mock(Claims.class);
        when(oldClaims.getId()).thenReturn(oldTokenId.toString());
        when(jwtService.parseRefreshToken("old-token")).thenReturn(oldClaims);
        when(refreshTokenRepository.findByTokenId(oldTokenId)).thenReturn(Optional.of(oldToken));

        Assertions.assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate("old-token"));

        // El token "actual" de la familia, ya revocado como efecto de la detección de reuso.
        UUID currentTokenId = UUID.randomUUID();
        RefreshToken currentToken = new RefreshToken();
        currentToken.setTokenId(currentTokenId);
        currentToken.setFamilyId(familyId);
        currentToken.setRevokedAt(Instant.now());

        Claims currentClaims = mock(Claims.class);
        when(currentClaims.getId()).thenReturn(currentTokenId.toString());
        when(jwtService.parseRefreshToken("current-token")).thenReturn(currentClaims);
        when(refreshTokenRepository.findByTokenId(currentTokenId)).thenReturn(Optional.of(currentToken));

        Assertions.assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate("current-token"));

        verify(refreshTokenRepository, times(2)).revokeAllActiveForFamily(eq(familyId), any(Instant.class));
    }

    private RotationTestResult rotateStoredToken(boolean rememberMe) {
        return rotateStoredToken(rememberMe, UUID.randomUUID());
    }

    private RotationTestResult rotateStoredToken(boolean rememberMe, UUID familyId) {
        UUID tokenId = UUID.randomUUID();
        String oldRawToken = "old-refresh-token";
        User user = new User();
        user.setId(1L);

        RefreshToken stored = new RefreshToken();
        stored.setId(100L);
        stored.setTokenId(tokenId);
        stored.setFamilyId(familyId);
        stored.setUser(user);
        stored.setTokenHash(hashToken(oldRawToken));
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        stored.setRememberMe(rememberMe);

        Claims oldClaims = mock(Claims.class);
        when(oldClaims.getId()).thenReturn(tokenId.toString());
        when(oldClaims.getSubject()).thenReturn(String.valueOf(user.getId()));
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
