package com.korofin.backend.controller.debt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korofin.backend.config.JwtProperties;
import com.korofin.backend.config.SecurityConfig;
import com.korofin.backend.dto.debt.DebtRequest;
import com.korofin.backend.dto.debt.DebtResponse;
import com.korofin.backend.dto.debt.DebtUpdateRequest;
import com.korofin.backend.exception.debt.DebtNotFoundException;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.JwtService;
import com.korofin.backend.service.debt.DebtService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DebtController.class)
@Import(SecurityConfig.class)
class DebtControllerTest {

    private static final String AUTH_HEADER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private DebtService debtService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private final DebtResponse loanDebt = new DebtResponse(
            1L, "Préstamo auto", new BigDecimal("12000000"), new BigDecimal("9000000"),
            new BigDecimal("1.50"), LocalDate.of(2027, 1, 31), null, null
    );

    @BeforeEach
    void setUp() {
        Claims mockClaims = org.mockito.Mockito.mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn("1");
        when(jwtService.parseAccessToken(any())).thenReturn(mockClaims);
        when(userRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void getDebtsReturns200WithPagedResults() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(debtService.getDebts(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(loanDebt), pageable, 1));

        mockMvc.perform(get("/api/debts").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].remainingAmount").value(9000000));
    }

    @Test
    void createDebtReturns201WhenValid() throws Exception {
        DebtRequest request = new DebtRequest(
                "Préstamo auto", new BigDecimal("12000000"), new BigDecimal("1.50"), LocalDate.of(2027, 1, 31)
        );
        when(debtService.createDebt(any(DebtRequest.class))).thenReturn(loanDebt);

        mockMvc.perform(post("/api/debts")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.totalAmount").value(12000000));
    }

    @Test
    void createDebtReturns400WhenTotalAmountIsZero() throws Exception {
        DebtRequest request = new DebtRequest("Préstamo", BigDecimal.ZERO, null, null);

        mockMvc.perform(post("/api/debts")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDebtReturns400WhenNameIsBlank() throws Exception {
        DebtRequest request = new DebtRequest("   ", new BigDecimal("1000"), null, null);

        mockMvc.perform(post("/api/debts")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDebtReturns400WhenInterestRateIsNegative() throws Exception {
        DebtRequest request = new DebtRequest("Préstamo", new BigDecimal("1000"), new BigDecimal("-0.5"), null);

        mockMvc.perform(post("/api/debts")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * El endpoint de actualización usa {@code DebtUpdateRequest}: un cliente que mande
     * {@code totalAmount} o {@code remainingAmount} no rompe la request, simplemente los ve
     * ignorados — el saldo solo cambia por un abono o un cargo registrado.
     */
    @Test
    void updateDebtIgnoresAmountFieldsSentByTheClient() throws Exception {
        String bodyWithAmounts = """
                {"name": "Renombrada", "interestRate": 1.20, "dueDate": "2027-01-31",
                 "totalAmount": 1, "remainingAmount": 0}
                """;
        when(debtService.updateDebt(eq(1L), any(DebtUpdateRequest.class))).thenReturn(loanDebt);

        mockMvc.perform(put("/api/debts/1")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithAmounts))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(12000000))
                .andExpect(jsonPath("$.remainingAmount").value(9000000));
    }

    @Test
    void updateDebtReturns404WhenDebtBelongsToAnotherUser() throws Exception {
        DebtUpdateRequest request = new DebtUpdateRequest("Renombrada", null, null);
        when(debtService.updateDebt(eq(99L), any(DebtUpdateRequest.class)))
                .thenThrow(new DebtNotFoundException());

        mockMvc.perform(put("/api/debts/99")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Deuda no encontrada"));
    }

    @Test
    void getDebtReturns404WhenDebtBelongsToAnotherUser() throws Exception {
        when(debtService.getDebt(99L)).thenThrow(new DebtNotFoundException());

        mockMvc.perform(get("/api/debts/99").header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDebtReturns204WhenDeletingOwnDebt() throws Exception {
        mockMvc.perform(delete("/api/debts/1").header("Authorization", AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteDebtReturns404WhenDebtBelongsToAnotherUser() throws Exception {
        doThrow(new DebtNotFoundException()).when(debtService).deleteDebt(99L);

        mockMvc.perform(delete("/api/debts/99").header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDebtsReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/debts"))
                .andExpect(status().isForbidden());
    }
}
