package com.korofin.backend.ai.controller;

import com.korofin.backend.common.config.JwtProperties;
import com.korofin.backend.common.config.SecurityConfig;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.JwtService;
import com.korofin.backend.ai.service.provider.AiProviderRegistry;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiProviderStatusController.class)
@Import(SecurityConfig.class)
class AiProviderStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiProviderRegistry registry;

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
    void statusReturns200WithEveryCatalogProviderAndNeverExposesApiKey() throws Exception {
        when(registry.status()).thenReturn(List.of(
                new AiProviderRegistry.AiProviderStatus("gemini", true, 1),
                new AiProviderRegistry.AiProviderStatus("nvidia", true, 2),
                new AiProviderRegistry.AiProviderStatus("opencode", false, null),
                new AiProviderRegistry.AiProviderStatus("openrouter", false, null),
                new AiProviderRegistry.AiProviderStatus("groq", false, null)
        ));

        mockMvc.perform(get("/api/ai/providers/status").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].name").value("gemini"))
                .andExpect(jsonPath("$[0].configured").value(true))
                .andExpect(jsonPath("$[0].priority").value(1))
                .andExpect(jsonPath("$[2].configured").value(false))
                .andExpect(jsonPath("$[2].priority").value(org.hamcrest.Matchers.nullValue()))
                // El DTO solo tiene name/configured/priority - nunca un campo de API key.
                .andExpect(jsonPath("$[0].apiKey").doesNotExist());
    }

    @Test
    void statusReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/ai/providers/status"))
                .andExpect(status().isForbidden());
    }
}
