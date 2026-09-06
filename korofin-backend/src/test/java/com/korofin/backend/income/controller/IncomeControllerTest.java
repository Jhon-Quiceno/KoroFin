package com.korofin.backend.income.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korofin.backend.common.config.JwtProperties;
import com.korofin.backend.common.config.SecurityConfig;
import com.korofin.backend.income.dto.IncomeRequest;
import com.korofin.backend.income.dto.IncomeResponse;
import com.korofin.backend.common.exception.ResourceNotFoundException;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.JwtService;
import com.korofin.backend.income.service.IncomeService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncomeController.class)
@Import(SecurityConfig.class)
class IncomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private IncomeService incomeService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String AUTH_HEADER = "Bearer test-token";

    private final IncomeResponse salaryIncome = new IncomeResponse(
            1L, BigDecimal.valueOf(1500), "Sueldo mensual", LocalDate.of(2026, 6, 5), 1L, "Salario"
    );

    @BeforeEach
    void setUp() {
        Claims mockClaims = org.mockito.Mockito.mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn("1");
        when(jwtService.parseAccessToken(any())).thenReturn(mockClaims);
        when(userRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void getIncomesReturns200WithPagedResults() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(incomeService.getIncomes(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(salaryIncome), pageable, 1));

        mockMvc.perform(get("/api/incomes").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getIncomesFiltersByMonthYear() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(incomeService.getIncomes(eq(6), eq(2026), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(salaryIncome), pageable, 1));

        mockMvc.perform(get("/api/incomes?month=6&year=2026").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void createIncomeReturns201WhenValid() throws Exception {
        IncomeRequest request = new IncomeRequest(
                BigDecimal.valueOf(1500), "Sueldo mensual", LocalDate.of(2026, 6, 5), 1L
        );
        when(incomeService.createIncome(any(IncomeRequest.class))).thenReturn(salaryIncome);

        mockMvc.perform(post("/api/incomes")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void createIncomeReturns400WhenAmountIsNegative() throws Exception {
        IncomeRequest request = new IncomeRequest(
                BigDecimal.valueOf(-10), "Sueldo mensual", LocalDate.of(2026, 6, 5), null
        );

        mockMvc.perform(post("/api/incomes")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIncomeReturns400WhenDateIsInTheFuture() throws Exception {
        IncomeRequest request = new IncomeRequest(
                BigDecimal.valueOf(100), "Sueldo mensual", LocalDate.now().plusDays(5), null
        );

        mockMvc.perform(post("/api/incomes")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateIncomeReturns200WhenUpdatingOwnIncome() throws Exception {
        IncomeRequest request = new IncomeRequest(
                BigDecimal.valueOf(1600), "Sueldo actualizado", LocalDate.of(2026, 6, 5), 1L
        );
        when(incomeService.updateIncome(eq(1L), any(IncomeRequest.class))).thenReturn(salaryIncome);

        mockMvc.perform(put("/api/incomes/1")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateIncomeReturns404WhenUpdatingAnotherUsersIncome() throws Exception {
        IncomeRequest request = new IncomeRequest(
                BigDecimal.valueOf(1600), "Sueldo actualizado", LocalDate.of(2026, 6, 5), null
        );
        when(incomeService.updateIncome(eq(99L), any(IncomeRequest.class)))
                .thenThrow(new ResourceNotFoundException("Ingreso no encontrado"));

        mockMvc.perform(put("/api/incomes/99")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteIncomeReturns204WhenDeletingOwnIncome() throws Exception {
        mockMvc.perform(delete("/api/incomes/1")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteIncomeReturns404WhenDeletingAnotherUsersIncome() throws Exception {
        doThrow(new ResourceNotFoundException("Ingreso no encontrado"))
                .when(incomeService).deleteIncome(1L);

        mockMvc.perform(delete("/api/incomes/1")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    void getIncomesReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/incomes"))
                .andExpect(status().isForbidden());
    }
}
