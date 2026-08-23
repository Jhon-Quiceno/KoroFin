package com.korofin.backend.user.service;

import com.korofin.backend.user.dto.LoginRequest;
import com.korofin.backend.user.dto.PasswordChangeRequest;
import com.korofin.backend.user.dto.ProfileUpdateRequest;
import com.korofin.backend.user.dto.RegisterRequest;
import com.korofin.backend.user.dto.UserPreferencesUpdateRequest;
import com.korofin.backend.user.dto.UserResponse;
import com.korofin.backend.ai.entity.AiMessageKind;
import com.korofin.backend.user.entity.AppLanguage;
import com.korofin.backend.user.entity.ThemePreference;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.common.exception.ResourceNotFoundException;
import com.korofin.backend.user.exception.EmailAlreadyExistsException;
import com.korofin.backend.user.exception.InvalidCredentialsException;
import com.korofin.backend.user.exception.InvalidRefreshTokenException;
import com.korofin.backend.user.mapper.UserMapper;
import com.korofin.backend.ai.repository.AiMessageRepository;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.JwtService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AiMessageRepository aiMessageRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void registerShouldCreateActiveUserAndReturnAuthResponseWithBothTokens() {
        RegisterRequest request = new RegisterRequest("Ana", " ANA@MAIL.COM ", "secret123");
        when(userRepository.existsByEmailIgnoreCase("ana@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            Assertions.assertTrue(user.isActive());
            user.setId(10L);
            return user;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createForUser(any(User.class), eq(false))).thenReturn("refresh-token");
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse(10L, "Ana", "ana@mail.com", ThemePreference.SYSTEM, "COP", AppLanguage.ES));

        AuthSession session = userService.register(request);

        Assertions.assertEquals("access-token", session.response().accessToken());
        Assertions.assertEquals("Bearer", session.response().tokenType());
        Assertions.assertEquals(900L, session.response().expiresIn());
        Assertions.assertEquals(10L, session.response().user().id());
        Assertions.assertEquals("refresh-token", session.response().refreshToken());
        Assertions.assertEquals("refresh-token", session.refreshToken());
        Assertions.assertFalse(session.rememberMe());
    }

    @Test
    void registerShouldFailWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("Ana", "ana@mail.com", "secret123");
        when(userRepository.existsByEmailIgnoreCase("ana@mail.com")).thenReturn(true);

        Assertions.assertThrows(EmailAlreadyExistsException.class, () -> userService.register(request));
    }

    @Test
    void loginShouldReturnAuthResponseForValidCredentials() {
        User user = new User();
        user.setId(7L);
        user.setName("John");
        user.setEmail("john@mail.com");
        user.setPasswordHash("hashed");
        user.setActive(true);
        when(userRepository.findByEmailIgnoreCase("john@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createForUser(any(User.class), eq(true))).thenReturn("refresh-token");
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse(7L, "John", "john@mail.com", ThemePreference.SYSTEM, "COP", AppLanguage.ES));

        AuthSession session = userService.login(new LoginRequest("JOHN@mail.com", "secret123", true));

        Assertions.assertEquals("access-token", session.response().accessToken());
        Assertions.assertEquals(7L, session.response().user().id());
        Assertions.assertEquals("refresh-token", session.refreshToken());
        Assertions.assertTrue(session.rememberMe());
        Assertions.assertNotNull(user.getLastLoginAt());
    }

    @Test
    void loginShouldDeleteChatHistoryButNotInsightHistory() {
        User user = new User();
        user.setId(7L);
        user.setName("John");
        user.setEmail("john@mail.com");
        user.setPasswordHash("hashed");
        user.setActive(true);
        when(userRepository.findByEmailIgnoreCase("john@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createForUser(any(User.class), eq(true))).thenReturn("refresh-token");
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse(7L, "John", "john@mail.com", ThemePreference.SYSTEM, "COP", AppLanguage.ES));

        userService.login(new LoginRequest("john@mail.com", "secret123", true));

        verify(aiMessageRepository).deleteByUserIdAndKind(7L, AiMessageKind.CHAT);
    }

    @Test
    void loginShouldTreatNullRememberMeAsFalse() {
        User user = new User();
        user.setId(8L);
        user.setName("Jane");
        user.setEmail("jane@mail.com");
        user.setPasswordHash("hashed");
        user.setActive(true);
        when(userRepository.findByEmailIgnoreCase("jane@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createForUser(any(User.class), eq(false))).thenReturn("refresh-token");
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse(8L, "Jane", "jane@mail.com", ThemePreference.SYSTEM, "COP", AppLanguage.ES));

        AuthSession session = userService.login(new LoginRequest("jane@mail.com", "secret123", null));

        Assertions.assertFalse(session.rememberMe());
    }

    @Test
    void loginShouldFailWhenPasswordIsInvalid() {
        User user = new User();
        user.setId(7L);
        user.setName("John");
        user.setEmail("john@mail.com");
        user.setPasswordHash("hashed");
        user.setActive(true);
        when(userRepository.findByEmailIgnoreCase("john@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-pass", "hashed")).thenReturn(false);

        Assertions.assertThrows(InvalidCredentialsException.class,
                () -> userService.login(new LoginRequest("john@mail.com", "bad-pass", false)));
    }

    @Test
    void loginShouldFailWhenUserIsInactive() {
        User user = new User();
        user.setId(9L);
        user.setName("Inactive");
        user.setEmail("inactive@mail.com");
        user.setPasswordHash("hashed");
        user.setActive(false);
        when(userRepository.findByEmailIgnoreCase("inactive@mail.com")).thenReturn(Optional.of(user));

        Assertions.assertThrows(InvalidCredentialsException.class,
                () -> userService.login(new LoginRequest("inactive@mail.com", "secret123", false)));
    }

    @Test
    void loginShouldFailWhenUserDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase("nope@mail.com")).thenReturn(Optional.empty());

        Assertions.assertThrows(InvalidCredentialsException.class,
                () -> userService.login(new LoginRequest("nope@mail.com", "secret123", false)));
    }

    @Test
    void refreshShouldFailWhenTokenIsMissing() {
        Assertions.assertThrows(InvalidRefreshTokenException.class, () -> userService.refresh(" "));
    }

    @Test
    void refreshShouldPropagateRememberMeFromRotationResultAndIncludeRefreshTokenInBody() {
        User user = new User();
        user.setId(11L);
        user.setName("Rotated");
        user.setEmail("rotated@mail.com");
        when(refreshTokenService.rotate("old-refresh-token"))
                .thenReturn(new RefreshTokenService.RotationResult(user, "new-refresh-token", true));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(userMapper.toResponse(user)).thenReturn(new UserResponse(11L, "Rotated", "rotated@mail.com", ThemePreference.SYSTEM, "COP", AppLanguage.ES));

        AuthSession session = userService.refresh("old-refresh-token");

        Assertions.assertEquals("new-refresh-token", session.refreshToken());
        Assertions.assertEquals("new-refresh-token", session.response().refreshToken());
        Assertions.assertTrue(session.rememberMe());
    }

    @Test
    void logoutDelegatesToRefreshTokenService() {
        userService.logout("some-token");

        verify(refreshTokenService).revoke("some-token");
    }

    @Test
    void updateProfileShouldUpdateNameAndEmailWhenEmailIsNotTaken() {
        User user = new User();
        user.setId(1L);
        user.setName("Old Name");
        user.setEmail("old@mail.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("new@mail.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse(1L, "New Name", "new@mail.com", ThemePreference.SYSTEM, "COP", AppLanguage.ES));

        UserResponse response = userService.updateProfile(1L, new ProfileUpdateRequest("New Name", "NEW@mail.com"));

        Assertions.assertEquals("New Name", response.name());
        Assertions.assertEquals("new@mail.com", response.email());
        Assertions.assertEquals("New Name", user.getName());
        Assertions.assertEquals("new@mail.com", user.getEmail());
    }

    @Test
    void updateProfileShouldAllowKeepingTheSameEmail() {
        User user = new User();
        user.setId(1L);
        user.setName("Old Name");
        user.setEmail("same@mail.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse(1L, "New Name", "same@mail.com", ThemePreference.SYSTEM, "COP", AppLanguage.ES));

        userService.updateProfile(1L, new ProfileUpdateRequest("New Name", "same@mail.com"));

        verify(userRepository, never()).existsByEmailIgnoreCase(any());
    }

    @Test
    void updateProfileShouldFailWhenEmailBelongsToAnotherUser() {
        User user = new User();
        user.setId(1L);
        user.setName("Old Name");
        user.setEmail("old@mail.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("taken@mail.com")).thenReturn(true);

        Assertions.assertThrows(EmailAlreadyExistsException.class,
                () -> userService.updateProfile(1L, new ProfileUpdateRequest("New Name", "taken@mail.com")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileShouldFailWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class,
                () -> userService.updateProfile(99L, new ProfileUpdateRequest("Name", "email@mail.com")));
    }

    @Test
    void updateProfileShouldMapDataIntegrityViolationToEmailAlreadyExistsException() {
        User user = new User();
        user.setId(1L);
        user.setName("Old Name");
        user.setEmail("old@mail.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("new@mail.com")).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_users_email"));

        Assertions.assertThrows(EmailAlreadyExistsException.class,
                () -> userService.updateProfile(1L, new ProfileUpdateRequest("New Name", "new@mail.com")));
    }

    @Test
    void changePasswordShouldUpdateHashWhenCurrentPasswordMatchesAndRevokeOtherSessions() {
        User user = new User();
        user.setId(1L);
        user.setPasswordHash("old-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPassword", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("new-hash");

        userService.changePassword(1L, new PasswordChangeRequest("oldPassword", "newPassword"));

        Assertions.assertEquals("new-hash", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void changePasswordShouldFailWhenCurrentPasswordDoesNotMatch() {
        User user = new User();
        user.setId(1L);
        user.setPasswordHash("old-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "old-hash")).thenReturn(false);

        Assertions.assertThrows(InvalidCredentialsException.class,
                () -> userService.changePassword(1L, new PasswordChangeRequest("wrongPassword", "newPassword")));
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void changePasswordShouldFailWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class,
                () -> userService.changePassword(99L, new PasswordChangeRequest("oldPassword", "newPassword")));
    }

    @Test
    void getPreferencesShouldReturnStoredValues() {
        User user = new User();
        user.setId(1L);
        user.setTheme(ThemePreference.DARK);
        user.setCurrency("USD");
        user.setLanguage(AppLanguage.EN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var response = userService.getPreferences(1L);

        Assertions.assertEquals(ThemePreference.DARK, response.theme());
        Assertions.assertEquals("USD", response.currency());
        Assertions.assertEquals(AppLanguage.EN, response.language());
    }

    @Test
    void getPreferencesShouldFailWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> userService.getPreferences(99L));
    }

    @Test
    void updatePreferencesShouldPersistNewValues() {
        User user = new User();
        user.setId(1L);
        user.setTheme(ThemePreference.SYSTEM);
        user.setCurrency("COP");
        user.setLanguage(AppLanguage.ES);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.updatePreferences(
                1L, new UserPreferencesUpdateRequest(ThemePreference.DARK, "USD", AppLanguage.EN));

        Assertions.assertEquals(ThemePreference.DARK, response.theme());
        Assertions.assertEquals("USD", response.currency());
        Assertions.assertEquals(AppLanguage.EN, response.language());
        Assertions.assertEquals(ThemePreference.DARK, user.getTheme());
        Assertions.assertEquals("USD", user.getCurrency());
        Assertions.assertEquals(AppLanguage.EN, user.getLanguage());
    }

    @Test
    void updatePreferencesShouldFailWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class,
                () -> userService.updatePreferences(99L, new UserPreferencesUpdateRequest(ThemePreference.DARK, "USD", AppLanguage.EN)));
    }
}
