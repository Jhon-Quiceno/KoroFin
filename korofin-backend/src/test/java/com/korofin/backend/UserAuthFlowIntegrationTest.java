package com.korofin.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.korofin.backend.user.dto.LoginRequest;
import com.korofin.backend.user.dto.RefreshRequest;
import com.korofin.backend.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración de punta a punta (controller -> service -> repository -> DB real vía
 * Testcontainers) del flujo crítico registro + login + refresh + acceso autenticado, según el
 * plan del backend (sección 15.1) — la única capa que detecta errores de wiring que las capas
 * aisladas (unit tests con mocks) no ven. Requiere Docker Desktop corriendo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAuthFlowIntegrationTest implements PostgresContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registerThenLoginThenRefreshThenAccessProtectedEndpoint() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("Ana", "ana-flow@korofin.dev", "secret123");

        String registerBody = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode registerJson = objectMapper.readTree(registerBody);
        String registerRefreshToken = registerJson.get("refreshToken").asText();

        // El login debe funcionar de forma independiente con las mismas credenciales.
        LoginRequest loginRequest = new LoginRequest("ana-flow@korofin.dev", "secret123", true);
        String loginBody = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("ana-flow@korofin.dev"))
                .andReturn().getResponse().getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginBody);
        String accessToken = loginJson.get("accessToken").asText();
        String loginRefreshToken = loginJson.get("refreshToken").asText();
        assertThat(loginRefreshToken).isNotEqualTo(registerRefreshToken);

        // El access token recién emitido debe permitir acceder a un endpoint protegido.
        mockMvc.perform(get("/api/users/preferences").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("SYSTEM"));

        // El refresh token rota: se emite uno nuevo y el viejo deja de servir.
        String refreshBody = mockMvc.perform(post("/api/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(loginRefreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode refreshJson = objectMapper.readTree(refreshBody);
        assertThat(refreshJson.get("refreshToken").asText()).isNotEqualTo(loginRefreshToken);

        mockMvc.perform(post("/api/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(loginRefreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registeringTheSameEmailTwiceReturns409() throws Exception {
        RegisterRequest request = new RegisterRequest("Bob", "duplicado@korofin.dev", "secret123");

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void changingPasswordRevokesOtherRefreshTokens() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("Carla", "carla-flow@korofin.dev", "secret123");
        String registerBody = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode registerJson = objectMapper.readTree(registerBody);
        String accessToken = registerJson.get("accessToken").asText();
        String refreshToken = registerJson.get("refreshToken").asText();

        mockMvc.perform(put("/api/users/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"secret123\",\"newPassword\":\"newSecret456\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }
}
