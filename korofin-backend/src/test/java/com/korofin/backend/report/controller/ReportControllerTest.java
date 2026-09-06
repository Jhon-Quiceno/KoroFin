package com.korofin.backend.report.controller;

import com.korofin.backend.common.config.JwtProperties;
import com.korofin.backend.common.config.SecurityConfig;
import com.korofin.backend.analysis.dto.CategoryTotalResponse;
import com.korofin.backend.report.dto.MonthlyReportResponse;
import com.korofin.backend.report.dto.MovementResponse;
import com.korofin.backend.report.dto.MovementType;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.JwtService;
import com.korofin.backend.report.service.ReportService;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    private static final String AUTH_HEADER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

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
    void getMonthlyReturns200() throws Exception {
        MonthlyReportResponse report = new MonthlyReportResponse(
                2026, 7, BigDecimal.valueOf(1000), BigDecimal.valueOf(600), BigDecimal.valueOf(400),
                BigDecimal.valueOf(40), List.of(new CategoryTotalResponse(1L, "Comida", BigDecimal.valueOf(300)))
        );
        when(reportService.getMonthlyReport(any(YearMonth.class))).thenReturn(report);

        mockMvc.perform(get("/api/reports/monthly").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(1000));
    }

    @Test
    void getMovementsReturns200() throws Exception {
        when(reportService.getMovements(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(new MovementResponse(
                        1L, MovementType.EXPENSE, LocalDate.of(2026, 7, 10), BigDecimal.valueOf(50),
                        "Café", "Comida"
                )));

        mockMvc.perform(get("/api/reports/movements?from=2026-07-01&to=2026-07-31")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Comida"));
    }

    @Test
    void exportCsvSetsAttachmentContentDispositionWithKorofinPrefix() throws Exception {
        when(reportService.getMovements(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/reports/export?from=2026-07-01&to=2026-07-31&format=csv")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("korofin-")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    void exportCsvEscapesFieldsContainingCommasAndQuotesPerRfc4180() throws Exception {
        when(reportService.getMovements(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(new MovementResponse(
                        1L, MovementType.EXPENSE, LocalDate.of(2026, 7, 10), BigDecimal.valueOf(50),
                        "Cena, con \"amigos\"", "Comida"
                )));

        mockMvc.perform(get("/api/reports/export?from=2026-07-01&to=2026-07-31&format=csv")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Cena, con \"\"amigos\"\"\"")));
    }

    @Test
    void exportCsvNeutralizesFormulaInjectionInDescriptionField() throws Exception {
        when(reportService.getMovements(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(new MovementResponse(
                        1L, MovementType.EXPENSE, LocalDate.of(2026, 7, 10), BigDecimal.valueOf(50),
                        "=cmd|'/c calc'!A1", "Comida"
                )));

        mockMvc.perform(get("/api/reports/export?from=2026-07-01&to=2026-07-31&format=csv")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("'=cmd")));
    }

    @Test
    void exportJsonSetsJsonAttachmentContentType() throws Exception {
        when(reportService.getMovements(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/reports/export?from=2026-07-01&to=2026-07-31&format=json")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".json")));
    }

    @Test
    void getMonthlyReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/reports/monthly"))
                .andExpect(status().isForbidden());
    }
}
