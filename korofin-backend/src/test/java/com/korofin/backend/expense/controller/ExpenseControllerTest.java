package com.korofin.backend.expense.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korofin.backend.common.config.JwtProperties;
import com.korofin.backend.common.config.SecurityConfig;
import com.korofin.backend.expense.dto.ExpenseRequest;
import com.korofin.backend.expense.dto.ExpenseResponse;
import com.korofin.backend.expense.entity.PaymentMethodType;
import com.korofin.backend.common.exception.ResourceNotFoundException;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.JwtService;
import com.korofin.backend.expense.service.ExpenseService;
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

@WebMvcTest(ExpenseController.class)
@Import(SecurityConfig.class)
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private ExpenseService expenseService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String AUTH_HEADER = "Bearer test-token";

    private final ExpenseResponse groceriesExpense = new ExpenseResponse(
            1L, BigDecimal.valueOf(85), "Supermercado", LocalDate.of(2026, 6, 5),
            PaymentMethodType.DEBIT_CARD, 2L, "Alimentación"
    );

    @BeforeEach
    void setUp() {
        Claims mockClaims = org.mockito.Mockito.mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn("1");
        when(jwtService.parseAccessToken(any())).thenReturn(mockClaims);
        when(userRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void getExpensesReturns200WithPagedResults() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(expenseService.getExpenses(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(groceriesExpense), pageable, 1));

        mockMvc.perform(get("/api/expenses").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].paymentMethod").value("DEBIT_CARD"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getExpensesFiltersByCategoryDateRangeAndPaymentMethod() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(expenseService.getExpenses(
                eq(2L), eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 30)),
                eq(PaymentMethodType.DEBIT_CARD), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(groceriesExpense), pageable, 1));

        mockMvc.perform(get("/api/expenses?categoryId=2&from=2026-06-01&to=2026-06-30&paymentMethod=DEBIT_CARD")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void createExpenseReturns201WhenValid() throws Exception {
        ExpenseRequest request = new ExpenseRequest(
                BigDecimal.valueOf(85), "Supermercado", LocalDate.of(2026, 6, 5), PaymentMethodType.DEBIT_CARD, 2L
        );
        when(expenseService.createExpense(any(ExpenseRequest.class))).thenReturn(groceriesExpense);

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void createExpenseReturns400WhenPaymentMethodIsMissing() throws Exception {
        String invalidBody = """
                {"amount": 85, "description": "Super", "date": "2026-06-05", "categoryId": 2}
                """;

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createExpenseReturns400WhenAmountIsZero() throws Exception {
        ExpenseRequest request = new ExpenseRequest(
                BigDecimal.ZERO, "Supermercado", LocalDate.of(2026, 6, 5), PaymentMethodType.CASH, null
        );

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateExpenseReturns200WhenUpdatingOwnExpense() throws Exception {
        ExpenseRequest request = new ExpenseRequest(
                BigDecimal.valueOf(90), "Supermercado actualizado", LocalDate.of(2026, 6, 5),
                PaymentMethodType.CREDIT_CARD, 2L
        );
        when(expenseService.updateExpense(eq(1L), any(ExpenseRequest.class))).thenReturn(groceriesExpense);

        mockMvc.perform(put("/api/expenses/1")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateExpenseReturns404WhenUpdatingAnotherUsersExpense() throws Exception {
        ExpenseRequest request = new ExpenseRequest(
                BigDecimal.valueOf(90), "Supermercado actualizado", LocalDate.of(2026, 6, 5),
                PaymentMethodType.CREDIT_CARD, null
        );
        when(expenseService.updateExpense(eq(99L), any(ExpenseRequest.class)))
                .thenThrow(new ResourceNotFoundException("Gasto no encontrado"));

        mockMvc.perform(put("/api/expenses/99")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteExpenseReturns204WhenDeletingOwnExpense() throws Exception {
        mockMvc.perform(delete("/api/expenses/1")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteExpenseReturns404WhenDeletingAnotherUsersExpense() throws Exception {
        doThrow(new ResourceNotFoundException("Gasto no encontrado"))
                .when(expenseService).deleteExpense(1L);

        mockMvc.perform(delete("/api/expenses/1")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    void getExpensesReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isForbidden());
    }
}
