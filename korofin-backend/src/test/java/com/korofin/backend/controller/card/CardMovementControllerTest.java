package com.korofin.backend.controller.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korofin.backend.config.JwtProperties;
import com.korofin.backend.config.SecurityConfig;
import com.korofin.backend.dto.card.CardMovementResponse;
import com.korofin.backend.dto.card.CardPaymentRequest;
import com.korofin.backend.dto.card.CardPurchaseRequest;
import com.korofin.backend.dto.card.InstallmentResponse;
import com.korofin.backend.entity.card.CardMovementType;
import com.korofin.backend.entity.card.InstallmentStatus;
import com.korofin.backend.exception.card.CardPaymentExceedsBalanceException;
import com.korofin.backend.exception.card.CardPurchaseOverLimitException;
import com.korofin.backend.exception.card.CreditCardNotFoundException;
import com.korofin.backend.exception.card.InstallmentAmountTooLowException;
import com.korofin.backend.exception.card.InstallmentPlanNotFoundException;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.JwtService;
import com.korofin.backend.service.card.CardMovementService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardMovementController.class)
@Import(SecurityConfig.class)
class CardMovementControllerTest {

    private static final String AUTH_HEADER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private CardMovementService cardMovementService;

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
    void registerPurchaseReturns201WithTheLinkedExpenseIdAndNewBalance() throws Exception {
        CardPurchaseRequest request = new CardPurchaseRequest(
                new BigDecimal("250000"), null, "Mercado", null
        );
        when(cardMovementService.registerPurchase(eq(1L), any(CardPurchaseRequest.class))).thenReturn(
                movementResponse(30L, CardMovementType.PURCHASE, "250000", "250000", 90L, null)
        );

        mockMvc.perform(post("/api/cards/1/purchases")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PURCHASE"))
                .andExpect(jsonPath("$.expenseId").value(90L))
                .andExpect(jsonPath("$.cardBalanceAfter").value(250000))
                .andExpect(jsonPath("$.installmentPlanId").doesNotExist());
    }

    @Test
    void registerInstallmentPurchaseReturns201WithBothExpenseIdAndPlanId() throws Exception {
        CardPurchaseRequest request = new CardPurchaseRequest(
                new BigDecimal("300000"), null, "TV", 3
        );
        when(cardMovementService.registerPurchase(eq(1L), any(CardPurchaseRequest.class))).thenReturn(
                movementResponse(31L, CardMovementType.INSTALLMENT_PURCHASE, "300000", "300000", 91L, 12L)
        );

        mockMvc.perform(post("/api/cards/1/purchases")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INSTALLMENT_PURCHASE"))
                .andExpect(jsonPath("$.expenseId").value(91L))
                .andExpect(jsonPath("$.installmentPlanId").value(12L));
    }

    @Test
    void registerPurchaseReturns400WhenItExceedsTheCreditLimit() throws Exception {
        CardPurchaseRequest request = new CardPurchaseRequest(new BigDecimal("99999999"), null, null, null);
        when(cardMovementService.registerPurchase(eq(1L), any(CardPurchaseRequest.class)))
                .thenThrow(new CardPurchaseOverLimitException());

        mockMvc.perform(post("/api/cards/1/purchases")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La compra supera el cupo disponible de la tarjeta"));
    }

    @Test
    void registerPurchaseReturns400WhenTheAmountIsTooLowForTheInstallmentCount() throws Exception {
        CardPurchaseRequest request = new CardPurchaseRequest(new BigDecimal("0.35"), null, null, 48);
        when(cardMovementService.registerPurchase(eq(1L), any(CardPurchaseRequest.class)))
                .thenThrow(new InstallmentAmountTooLowException());

        mockMvc.perform(post("/api/cards/1/purchases")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerPurchaseReturns400WhenInstallmentCountIsAboveTheAllowedMaximum() throws Exception {
        CardPurchaseRequest request = new CardPurchaseRequest(new BigDecimal("300000"), null, null, 49);

        mockMvc.perform(post("/api/cards/1/purchases")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerPurchaseReturns400WhenTheAmountHasMoreThanTwoDecimals() throws Exception {
        CardPurchaseRequest request = new CardPurchaseRequest(new BigDecimal("10.999"), null, null, null);

        mockMvc.perform(post("/api/cards/1/purchases")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerPurchaseReturns404WhenCardBelongsToAnotherUser() throws Exception {
        CardPurchaseRequest request = new CardPurchaseRequest(new BigDecimal("1000"), null, null, null);
        when(cardMovementService.registerPurchase(eq(99L), any(CardPurchaseRequest.class)))
                .thenThrow(new CreditCardNotFoundException());

        mockMvc.perform(post("/api/cards/99/purchases")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    /** Un pago nunca devuelve expenseId: pagar la tarjeta no genera un gasto nuevo. */
    @Test
    void registerPaymentReturns201WithoutAnyLinkedExpense() throws Exception {
        CardPaymentRequest request = new CardPaymentRequest(new BigDecimal("100000"), null, "Pago");
        when(cardMovementService.registerPayment(eq(1L), any(CardPaymentRequest.class))).thenReturn(
                movementResponse(33L, CardMovementType.PAYMENT, "100000", "150000", null, null)
        );

        mockMvc.perform(post("/api/cards/1/payments")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PAYMENT"))
                .andExpect(jsonPath("$.expenseId").doesNotExist())
                .andExpect(jsonPath("$.cardBalanceAfter").value(150000));
    }

    @Test
    void registerPaymentReturns400WhenItExceedsTheCurrentBalance() throws Exception {
        CardPaymentRequest request = new CardPaymentRequest(new BigDecimal("99999999"), null, null);
        when(cardMovementService.registerPayment(eq(1L), any(CardPaymentRequest.class)))
                .thenThrow(new CardPaymentExceedsBalanceException());

        mockMvc.perform(post("/api/cards/1/payments")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El pago no puede superar el saldo actual de la tarjeta"));
    }

    @Test
    void registerPaymentReturns400WhenTheAmountHasMoreThanTwoDecimals() throws Exception {
        CardPaymentRequest request = new CardPaymentRequest(new BigDecimal("10.999"), null, null);

        mockMvc.perform(post("/api/cards/1/payments")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMovementsReturns200AndForwardsTheTypeFilter() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(cardMovementService.getMovements(eq(1L), eq(CardMovementType.PURCHASE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        movementResponse(30L, CardMovementType.PURCHASE, "250000", null, null, null)
                ), pageable, 1));

        mockMvc.perform(get("/api/cards/1/movements?type=PURCHASE").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("PURCHASE"));
    }

    @Test
    void getMovementsReturns400WhenTheTypeFilterIsNotAValidMovementType() throws Exception {
        mockMvc.perform(get("/api/cards/1/movements?type=INEXISTENTE").header("Authorization", AUTH_HEADER))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getInstallmentsReturns200WithTheFrozenSchedule() throws Exception {
        when(cardMovementService.getInstallments(1L, 31L)).thenReturn(List.of(
                new InstallmentResponse(1L, 1, new BigDecimal("100000"), new BigDecimal("6000"),
                        LocalDate.of(2026, 6, 15), InstallmentStatus.PENDING),
                new InstallmentResponse(2L, 2, new BigDecimal("100000"), new BigDecimal("4000"),
                        LocalDate.of(2026, 7, 15), InstallmentStatus.PENDING)
        ));

        mockMvc.perform(get("/api/cards/1/movements/31/installments").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getInstallmentsReturns404WhenTheMovementHasNoInstallmentPlan() throws Exception {
        when(cardMovementService.getInstallments(1L, 30L))
                .thenThrow(new InstallmentPlanNotFoundException());

        mockMvc.perform(get("/api/cards/1/movements/30/installments").header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Plan de cuotas no encontrado"));
    }

    /**
     * El ledger de movimientos es inmutable: sobre las rutas que sí existen solo se aceptan los
     * verbos declarados ({@code GET} para listar, {@code POST} para crear). Un {@code PUT} o un
     * {@code DELETE} devuelven 405 porque no hay ningún handler que los mapee.
     */
    @Test
    void thereIsNoUpdateNorDeleteEndpointForCardMovements() throws Exception {
        mockMvc.perform(put("/api/cards/1/movements")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/cards/1/movements")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(put("/api/cards/1/purchases")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void getMovementsReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/cards/1/movements"))
                .andExpect(status().isForbidden());
    }

    private CardMovementResponse movementResponse(
            Long id, CardMovementType type, String amount, String balanceAfter, Long expenseId, Long planId
    ) {
        return new CardMovementResponse(
                id, 1L, type, new BigDecimal(amount), LocalDate.of(2026, 6, 10), "Mercado",
                balanceAfter != null ? new BigDecimal(balanceAfter) : null, expenseId, planId, null
        );
    }
}
