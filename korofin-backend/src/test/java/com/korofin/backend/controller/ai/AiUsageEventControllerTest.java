package com.korofin.backend.controller.ai;

import com.korofin.backend.config.SecurityConfig;
import com.korofin.backend.dto.ai.AiUsageEventSummaryResponse;
import com.korofin.backend.entity.ai.AiUsageEventType;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.JwtService;
import com.korofin.backend.service.ai.AiUsageEventService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiUsageEventController.class)
@Import(SecurityConfig.class)
class AiUsageEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiUsageEventService aiUsageEventService;

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
    void getUsageSummaryReturns200WithSummaryForExplicitPeriod() throws Exception {
        YearMonth period = YearMonth.of(2026, 7);
        when(aiUsageEventService.getUsageSummary(eq(period))).thenReturn(
                new AiUsageEventSummaryResponse(period, 320L, 5L, Map.of(AiUsageEventType.CHAT, 3L, AiUsageEventType.CATEGORIZE, 2L))
        );

        mockMvc.perform(get("/api/ai/usage")
                        .header("Authorization", AUTH_HEADER)
                        .param("period", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTokens").value(320))
                .andExpect(jsonPath("$.totalEvents").value(5));
    }

    @Test
    void getUsageSummaryDefaultsToCurrentPeriodWhenOmitted() throws Exception {
        when(aiUsageEventService.getUsageSummary(any(YearMonth.class))).thenReturn(
                new AiUsageEventSummaryResponse(YearMonth.now(), 0L, 0L, Map.of())
        );

        mockMvc.perform(get("/api/ai/usage")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(0));
    }

    @Test
    void getUsageSummaryReturns400ForMalformedPeriod() throws Exception {
        mockMvc.perform(get("/api/ai/usage")
                        .header("Authorization", AUTH_HEADER)
                        .param("period", "not-a-period"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUsageSummaryReturns403WithoutToken() throws Exception {
        mockMvc.perform(get("/api/ai/usage"))
                .andExpect(status().isForbidden());
    }
}
