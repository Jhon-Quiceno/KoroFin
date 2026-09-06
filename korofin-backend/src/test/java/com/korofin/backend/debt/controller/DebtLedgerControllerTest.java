package com.korofin.backend.debt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korofin.backend.common.config.JwtProperties;
import com.korofin.backend.common.config.SecurityConfig;
import com.korofin.backend.debt.dto.DebtChargeRequest;
import com.korofin.backend.debt.dto.DebtChargeResponse;
import com.korofin.backend.debt.dto.DebtPaymentRequest;
import com.korofin.backend.debt.dto.DebtPaymentResponse;
import com.korofin.backend.debt.dto.DebtResponse;
import com.korofin.backend.debt.exception.DebtNotFoundException;
import com.korofin.backend.debt.exception.DebtPaymentExceedsBalanceException;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.JwtService;
import com.korofin.backend.debt.service.DebtChargeService;
import com.korofin.backend.debt.service.DebtPaymentService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints del ledger de una deuda: abonos y cargos. Ambos controllers comparten test porque
 * comparten la misma superficie (crear + listar, sin actualizar ni borrar).
 */
@WebMvcTest({DebtPaymentController.class, DebtChargeController.class})
@Import(SecurityConfig.class)
class DebtLedgerControllerTest {

    private static final String AUTH_HEADER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private DebtPaymentService debtPaymentService;

    @MockitoBean
    private DebtChargeService debtChargeService;

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
    void createPaymentReturns201WithTheLinkedExpenseId() throws Exception {
        DebtPaymentRequest request = new DebtPaymentRequest(new BigDecimal("200000"), null, "Abono");
        when(debtPaymentService.createPayment(eq(1L), any(DebtPaymentRequest.class))).thenReturn(
                new DebtPaymentResponse(40L, 1L, new BigDecimal("200000"),
                        LocalDate.of(2026, 6, 10), "Abono", null, 70L)
        );

        mockMvc.perform(post("/api/debts/1/payments")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(40L))
                .andExpect(jsonPath("$.expenseId").value(70L));
    }

    @Test
    void createPaymentReturns400WhenAmountExceedsTheRemainingBalance() throws Exception {
        DebtPaymentRequest request = new DebtPaymentRequest(new BigDecimal("999999999"), null, null);
        when(debtPaymentService.createPayment(eq(1L), any(DebtPaymentRequest.class)))
                .thenThrow(new DebtPaymentExceedsBalanceException());

        mockMvc.perform(post("/api/debts/1/payments")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El abono no puede superar el saldo restante de la deuda"));
    }

    @Test
    void createPaymentReturns400WhenAmountIsNegative() throws Exception {
        DebtPaymentRequest request = new DebtPaymentRequest(new BigDecimal("-1"), null, null);

        mockMvc.perform(post("/api/debts/1/payments")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPaymentReturns400WhenTheAmountHasMoreThanTwoDecimals() throws Exception {
        DebtPaymentRequest request = new DebtPaymentRequest(new BigDecimal("10.999"), null, null);

        mockMvc.perform(post("/api/debts/1/payments")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPaymentReturns404WhenDebtBelongsToAnotherUser() throws Exception {
        DebtPaymentRequest request = new DebtPaymentRequest(new BigDecimal("1000"), null, null);
        when(debtPaymentService.createPayment(eq(99L), any(DebtPaymentRequest.class)))
                .thenThrow(new DebtNotFoundException());

        mockMvc.perform(post("/api/debts/99/payments")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPaymentsReturns200WithPagedResults() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(debtPaymentService.getPayments(eq(1L), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(new DebtPaymentResponse(
                        40L, 1L, new BigDecimal("200000"), LocalDate.of(2026, 6, 10), null, null, null
                )), pageable, 1)
        );

        mockMvc.perform(get("/api/debts/1/payments").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    /** Un cargo devuelve la deuda actualizada, no el cargo: lo que importa es el saldo nuevo. */
    @Test
    void createChargeReturns201WithTheUpdatedDebt() throws Exception {
        DebtChargeRequest request = new DebtChargeRequest(new BigDecimal("30000"), null, "Interés");
        when(debtChargeService.createCharge(eq(1L), any(DebtChargeRequest.class))).thenReturn(
                new DebtResponse(1L, "Préstamo", new BigDecimal("1000000"), new BigDecimal("1030000"),
                        null, null, null, null)
        );

        mockMvc.perform(post("/api/debts/1/charges")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remainingAmount").value(1030000));
    }

    @Test
    void createChargeReturns400WhenTheDateIsInTheFuture() throws Exception {
        String futureBody = """
                {"amount": 30000, "chargeDate": "2099-01-01"}
                """;

        mockMvc.perform(post("/api/debts/1/charges")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(futureBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createChargeReturns400WhenTheAmountHasMoreThanTwoDecimals() throws Exception {
        DebtChargeRequest request = new DebtChargeRequest(new BigDecimal("10.999"), null, null);

        mockMvc.perform(post("/api/debts/1/charges")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createChargeReturns404WhenDebtBelongsToAnotherUser() throws Exception {
        DebtChargeRequest request = new DebtChargeRequest(new BigDecimal("1000"), null, null);
        when(debtChargeService.createCharge(eq(99L), any(DebtChargeRequest.class)))
                .thenThrow(new DebtNotFoundException());

        mockMvc.perform(post("/api/debts/99/charges")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getChargesReturns200WithPagedResults() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(debtChargeService.getCharges(eq(1L), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(new DebtChargeResponse(
                        21L, 1L, new BigDecimal("30000"), LocalDate.of(2026, 6, 10), "Interés", null
                )), pageable, 1)
        );

        mockMvc.perform(get("/api/debts/1/charges").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(30000));
    }

    /**
     * El ledger es inmutable: sobre las rutas que sí existen solo se aceptan {@code GET} (listar) y
     * {@code POST} (crear). Un {@code PUT}, {@code PATCH} o {@code DELETE} devuelven 405 porque no
     * hay ningún handler que los mapee.
     */
    @Test
    void thereIsNoUpdateNorDeleteEndpointForPaymentsNorCharges() throws Exception {
        mockMvc.perform(put("/api/debts/1/payments")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/debts/1/payments")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(patch("/api/debts/1/charges")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void getPaymentsReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/debts/1/payments"))
                .andExpect(status().isForbidden());
    }
}
