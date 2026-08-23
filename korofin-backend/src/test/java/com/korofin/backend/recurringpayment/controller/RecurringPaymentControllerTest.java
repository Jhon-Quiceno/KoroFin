package com.korofin.backend.recurringpayment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korofin.backend.common.config.JwtProperties;
import com.korofin.backend.common.config.SecurityConfig;
import com.korofin.backend.recurringpayment.dto.RecurringPaymentPayResponse;
import com.korofin.backend.recurringpayment.dto.RecurringPaymentRequest;
import com.korofin.backend.recurringpayment.dto.RecurringPaymentResponse;
import com.korofin.backend.recurringpayment.dto.RecurringPaymentUpdateRequest;
import com.korofin.backend.recurringpayment.entity.RecurringFrequency;
import com.korofin.backend.common.exception.ResourceNotFoundException;
import com.korofin.backend.recurringpayment.exception.RecurringPaymentAlreadyPaidException;
import com.korofin.backend.recurringpayment.exception.RecurringPaymentNotDueYetException;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.JwtService;
import com.korofin.backend.recurringpayment.service.RecurringPaymentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecurringPaymentController.class)
@Import(SecurityConfig.class)
class RecurringPaymentControllerTest {

    private static final String AUTH_HEADER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private RecurringPaymentService recurringPaymentService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private final RecurringPaymentResponse netflix = new RecurringPaymentResponse(
            1L, "Netflix", new BigDecimal("35000"), RecurringFrequency.MONTHLY,
            LocalDate.of(2026, 7, 1), true, null, null
    );

    @BeforeEach
    void setUp() {
        Claims mockClaims = org.mockito.Mockito.mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn("1");
        when(jwtService.parseAccessToken(any())).thenReturn(mockClaims);
        when(userRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void getRecurringPaymentsReturns200WithPagedResults() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(recurringPaymentService.getRecurringPayments(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(netflix), pageable, 1));

        mockMvc.perform(get("/api/recurring").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Netflix"));
    }

    @Test
    void createRecurringPaymentReturns201WhenValid() throws Exception {
        RecurringPaymentRequest request = new RecurringPaymentRequest(
                "Netflix", new BigDecimal("35000"), RecurringFrequency.MONTHLY, LocalDate.of(2026, 7, 1)
        );
        when(recurringPaymentService.createRecurringPayment(any(RecurringPaymentRequest.class))).thenReturn(netflix);

        mockMvc.perform(post("/api/recurring")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void createRecurringPaymentReturns400WhenAmountIsZero() throws Exception {
        RecurringPaymentRequest request = new RecurringPaymentRequest(
                "Netflix", BigDecimal.ZERO, RecurringFrequency.MONTHLY, LocalDate.of(2026, 7, 1)
        );

        mockMvc.perform(post("/api/recurring")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRecurringPaymentReturns200() throws Exception {
        RecurringPaymentUpdateRequest request = new RecurringPaymentUpdateRequest(
                "Renombrado", new BigDecimal("40000"), RecurringFrequency.MONTHLY
        );
        when(recurringPaymentService.updateRecurringPayment(eq(1L), any(RecurringPaymentUpdateRequest.class)))
                .thenReturn(netflix);

        mockMvc.perform(put("/api/recurring/1")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateRecurringPaymentReturns404WhenOwnedByAnotherUser() throws Exception {
        RecurringPaymentUpdateRequest request = new RecurringPaymentUpdateRequest(
                "Renombrado", new BigDecimal("40000"), RecurringFrequency.MONTHLY
        );
        when(recurringPaymentService.updateRecurringPayment(eq(99L), any(RecurringPaymentUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Pago recurrente no encontrado"));

        mockMvc.perform(put("/api/recurring/99")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRecurringPaymentReturns204() throws Exception {
        mockMvc.perform(delete("/api/recurring/1").header("Authorization", AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRecurringPaymentReturns404WhenOwnedByAnotherUser() throws Exception {
        doThrow(new ResourceNotFoundException("Pago recurrente no encontrado"))
                .when(recurringPaymentService).deleteRecurringPayment(99L);

        mockMvc.perform(delete("/api/recurring/99").header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    void toggleRecurringPaymentReturns200() throws Exception {
        when(recurringPaymentService.toggleRecurringPayment(1L)).thenReturn(netflix);

        mockMvc.perform(patch("/api/recurring/1/toggle").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk());
    }

    @Test
    void payRecurringPaymentReturns200WithExpenseId() throws Exception {
        when(recurringPaymentService.payRecurringPayment(1L))
                .thenReturn(new RecurringPaymentPayResponse(netflix, 42L));

        mockMvc.perform(patch("/api/recurring/1/pay").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseId").value(42));
    }

    @Test
    void payRecurringPaymentReturns409WhenNotDueYet() throws Exception {
        when(recurringPaymentService.payRecurringPayment(1L))
                .thenThrow(new RecurringPaymentNotDueYetException("Este servicio vence el 2026-07-01"));

        mockMvc.perform(patch("/api/recurring/1/pay").header("Authorization", AUTH_HEADER))
                .andExpect(status().isConflict());
    }

    @Test
    void payRecurringPaymentReturns409WhenAlreadyPaidConcurrently() throws Exception {
        when(recurringPaymentService.payRecurringPayment(1L))
                .thenThrow(new RecurringPaymentAlreadyPaidException("Este servicio ya fue marcado como pagado"));

        mockMvc.perform(patch("/api/recurring/1/pay").header("Authorization", AUTH_HEADER))
                .andExpect(status().isConflict());
    }

    @Test
    void getRecurringPaymentsReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/recurring"))
                .andExpect(status().isForbidden());
    }
}
