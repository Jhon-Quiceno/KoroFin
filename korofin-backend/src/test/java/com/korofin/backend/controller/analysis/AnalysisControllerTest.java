package com.korofin.backend.controller.analysis;

import com.korofin.backend.config.JwtProperties;
import com.korofin.backend.config.SecurityConfig;
import com.korofin.backend.dto.analysis.CategoryTotalResponse;
import com.korofin.backend.dto.analysis.FinancialSummaryResponse;
import com.korofin.backend.dto.analysis.MonthEndPredictionResponse;
import com.korofin.backend.dto.analysis.RecommendationResponse;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.JwtService;
import com.korofin.backend.service.analysis.FinancialAnalysisService;
import com.korofin.backend.service.analysis.MonthEndPredictionService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
@Import(SecurityConfig.class)
class AnalysisControllerTest {

    private static final String AUTH_HEADER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialAnalysisService financialAnalysisService;

    @MockitoBean
    private MonthEndPredictionService monthEndPredictionService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
        Claims mockClaims = org.mockito.Mockito.mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn("1");
        when(jwtService.parseAccessToken(any())).thenReturn(mockClaims);
        when(userRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void getSummaryReturns200() throws Exception {
        FinancialSummaryResponse summary = new FinancialSummaryResponse(
                2026, 7, BigDecimal.valueOf(1000), BigDecimal.valueOf(600), BigDecimal.valueOf(400),
                BigDecimal.valueOf(40), List.of(new CategoryTotalResponse(1L, "Comida", BigDecimal.valueOf(300))),
                List.of()
        );
        when(financialAnalysisService.getSummary(any(YearMonth.class))).thenReturn(summary);

        mockMvc.perform(get("/api/analysis/summary").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(1000))
                .andExpect(jsonPath("$.topExpenseCategories[0].categoryName").value("Comida"));
    }

    @Test
    void getRecommendationsReturns200() throws Exception {
        when(financialAnalysisService.getRecommendations())
                .thenReturn(List.of(new RecommendationResponse("Vas bien", "Seguí así")));

        mockMvc.perform(get("/api/analysis/recommendations").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Vas bien"));
    }

    @Test
    void getPredictionReturns200() throws Exception {
        MonthEndPredictionResponse prediction = new MonthEndPredictionResponse(
                2026, 7, BigDecimal.valueOf(300), BigDecimal.valueOf(20), BigDecimal.valueOf(620), 15, 31
        );
        when(monthEndPredictionService.predict()).thenReturn(prediction);

        mockMvc.perform(get("/api/analysis/prediction").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectedExpense").value(620))
                .andExpect(jsonPath("$.daysInMonth").value(31));
    }

    @Test
    void getSummaryReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/analysis/summary"))
                .andExpect(status().isForbidden());
    }
}
