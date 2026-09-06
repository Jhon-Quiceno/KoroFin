package com.korofin.backend.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.korofin.backend.common.config.JwtProperties;
import com.korofin.backend.common.config.SecurityConfig;
import com.korofin.backend.user.dto.AuthResponse;
import com.korofin.backend.user.dto.LoginRequest;
import com.korofin.backend.user.dto.PasswordChangeRequest;
import com.korofin.backend.user.dto.ProfileUpdateRequest;
import com.korofin.backend.user.dto.RefreshRequest;
import com.korofin.backend.user.dto.RegisterRequest;
import com.korofin.backend.user.dto.UserPreferencesResponse;
import com.korofin.backend.user.dto.UserPreferencesUpdateRequest;
import com.korofin.backend.user.dto.UserResponse;
import com.korofin.backend.user.entity.AppLanguage;
import com.korofin.backend.user.entity.ThemePreference;
import com.korofin.backend.user.exception.EmailAlreadyExistsException;
import com.korofin.backend.user.exception.InvalidCredentialsException;
import com.korofin.backend.user.exception.InvalidRefreshTokenException;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.JwtService;
import com.korofin.backend.user.service.AuthSession;
import com.korofin.backend.user.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String AUTH_HEADER = "Bearer test-token";

    @BeforeEach
    void setUp() {
        Claims mockClaims = org.mockito.Mockito.mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn("1");
        when(jwtService.parseAccessToken(any())).thenReturn(mockClaims);
        when(userRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void registerReturns201WithAccessAndRefreshTokenInBody() throws Exception {
        RegisterRequest request = new RegisterRequest("Jane", "jane@example.com", "secret123");
        UserResponse userResponse = new UserResponse(1L, "Jane", "jane@example.com", ThemePreference.SYSTEM, "COP", AppLanguage.ES);
        AuthSession session = new AuthSession(
                new AuthResponse("access-token", "Bearer", 900L, userResponse, "refresh-token"),
                "refresh-token",
                false
        );
        when(userService.register(any(RegisterRequest.class))).thenReturn(session);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void registerReturns400WhenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest("Jane", "not-an-email", "secret123");

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerReturns409WhenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest("Jane", "jane@example.com", "secret123");
        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("Ya existe un usuario registrado con este correo electrónico"));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReturns200WithAccessAndRefreshTokenInBody() throws Exception {
        LoginRequest request = new LoginRequest("jane@example.com", "secret123", true);
        UserResponse userResponse = new UserResponse(1L, "Jane", "jane@example.com", ThemePreference.SYSTEM, "COP", AppLanguage.ES);
        AuthSession session = new AuthSession(
                new AuthResponse("access-token", "Bearer", 900L, userResponse, "refresh-token"),
                "refresh-token",
                true
        );
        when(userService.login(any(LoginRequest.class))).thenReturn(session);

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.user.email").value("jane@example.com"));
    }

    @Test
    void loginReturns401WhenCredentialsAreInvalid() throws Exception {
        LoginRequest request = new LoginRequest("jane@example.com", "wrong-pass", false);
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Correo o contraseña inválidos"));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshReturns200AndDelegatesTokenFromRequestBody() throws Exception {
        UserResponse userResponse = new UserResponse(1L, "Jane", "jane@example.com", ThemePreference.SYSTEM, "COP", AppLanguage.ES);
        AuthSession session = new AuthSession(
                new AuthResponse("access-token", "Bearer", 900L, userResponse, "new-refresh-token"),
                "new-refresh-token",
                false
        );
        when(userService.refresh("stored-token")).thenReturn(session);

        mockMvc.perform(post("/api/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("stored-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));

        verify(userService).refresh("stored-token");
    }

    @Test
    void refreshReturns401WhenTokenIsInvalid() throws Exception {
        when(userService.refresh("bad-token"))
                .thenThrow(new InvalidRefreshTokenException("Refresh token inválido"));

        mockMvc.perform(post("/api/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("bad-token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshReturns400WhenBodyIsMissingToken() throws Exception {
        mockMvc.perform(post("/api/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutReturns204AndDelegatesTokenFromRequestBody() throws Exception {
        mockMvc.perform(post("/api/users/logout")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("stored-token"))))
                .andExpect(status().isNoContent());

        verify(userService).logout("stored-token");
    }

    @Test
    void logoutReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(post("/api/users/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("stored-token"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProfileReturns200WithUpdatedUserWhenValid() throws Exception {
        ProfileUpdateRequest request = new ProfileUpdateRequest("Jane Doe", "jane@example.com");
        UserResponse response = new UserResponse(1L, "Jane Doe", "jane@example.com", ThemePreference.SYSTEM, "COP", AppLanguage.ES);
        when(userService.updateProfile(eq(1L), any(ProfileUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/users/profile")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void updateProfileReturns409WhenEmailAlreadyTakenByAnotherUser() throws Exception {
        ProfileUpdateRequest request = new ProfileUpdateRequest("Jane Doe", "taken@example.com");
        when(userService.updateProfile(eq(1L), any(ProfileUpdateRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("Ya existe un usuario registrado con este correo electrónico"));

        mockMvc.perform(put("/api/users/profile")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateProfileReturns400WhenEmailIsBlank() throws Exception {
        ProfileUpdateRequest request = new ProfileUpdateRequest("Jane Doe", "");

        mockMvc.perform(put("/api/users/profile")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfileReturns403WithoutAuthToken() throws Exception {
        ProfileUpdateRequest request = new ProfileUpdateRequest("Jane Doe", "jane@example.com");

        mockMvc.perform(put("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void changePasswordReturns204WhenCurrentPasswordIsCorrect() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest("oldPassword", "newPassword123");

        mockMvc.perform(put("/api/users/password")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePasswordReturns401WhenCurrentPasswordIsWrong() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest("wrongPassword", "newPassword123");
        doThrow(new InvalidCredentialsException("La contraseña actual no es correcta"))
                .when(userService).changePassword(eq(1L), any(PasswordChangeRequest.class));

        mockMvc.perform(put("/api/users/password")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordReturns400WhenNewPasswordIsTooShort() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest("oldPassword", "abc");

        mockMvc.perform(put("/api/users/password")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordReturns403WithoutAuthToken() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest("oldPassword", "newPassword123");

        mockMvc.perform(put("/api/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPreferencesReturns200WithStoredValues() throws Exception {
        when(userService.getPreferences(1L))
                .thenReturn(new UserPreferencesResponse(ThemePreference.DARK, "USD", AppLanguage.EN));

        mockMvc.perform(get("/api/users/preferences").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.language").value("EN"));
    }

    @Test
    void getPreferencesReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/users/preferences"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePreferencesReturns200WithUpdatedValues() throws Exception {
        UserPreferencesUpdateRequest request = new UserPreferencesUpdateRequest(ThemePreference.DARK, "USD", AppLanguage.EN);
        when(userService.updatePreferences(eq(1L), any(UserPreferencesUpdateRequest.class)))
                .thenReturn(new UserPreferencesResponse(ThemePreference.DARK, "USD", AppLanguage.EN));

        mockMvc.perform(patch("/api/users/preferences")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.language").value("EN"));
    }

    @Test
    void updatePreferencesReturns400WhenCurrencyIsNotSupported() throws Exception {
        String invalidBody = "{\"theme\":\"DARK\",\"currency\":\"XXX\",\"language\":\"EN\"}";

        mockMvc.perform(patch("/api/users/preferences")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePreferencesReturns403WithoutAuthToken() throws Exception {
        UserPreferencesUpdateRequest request = new UserPreferencesUpdateRequest(ThemePreference.DARK, "USD", AppLanguage.EN);

        mockMvc.perform(patch("/api/users/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
