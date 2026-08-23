package com.korofin.backend.controller.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korofin.backend.config.JwtProperties;
import com.korofin.backend.config.SecurityConfig;
import com.korofin.backend.dto.card.CreditCardRequest;
import com.korofin.backend.dto.card.CreditCardResponse;
import com.korofin.backend.dto.card.CreditCardUpdateRequest;
import com.korofin.backend.entity.card.CardFranchise;
import com.korofin.backend.exception.card.CreditCardNotFoundException;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.JwtService;
import com.korofin.backend.service.card.CreditCardService;
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

@WebMvcTest(CreditCardController.class)
@Import(SecurityConfig.class)
class CreditCardControllerTest {

    private static final String AUTH_HEADER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private CreditCardService creditCardService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private final CreditCardResponse visaCard = new CreditCardResponse(
            1L, "Visa Oro", "Bancolombia", CardFranchise.VISA, new BigDecimal("5000000"),
            new BigDecimal("0.0250"), 15, 5, new BigDecimal("1200000"), new BigDecimal("3800000"),
            null, null, null
    );

    @BeforeEach
    void setUp() {
        Claims mockClaims = org.mockito.Mockito.mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn("1");
        when(jwtService.parseAccessToken(any())).thenReturn(mockClaims);
        when(userRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void getCardsReturns200WithDerivedAvailableCredit() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(creditCardService.getCards(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(visaCard), pageable, 1));

        mockMvc.perform(get("/api/cards").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].availableCredit").value(3800000))
                .andExpect(jsonPath("$.content[0].franchise").value("VISA"));
    }

    @Test
    void createCardReturns201WhenValid() throws Exception {
        CreditCardRequest request = new CreditCardRequest(
                "Visa Oro", "Bancolombia", CardFranchise.VISA,
                new BigDecimal("5000000"), new BigDecimal("0.0250"), 15, 5
        );
        when(creditCardService.createCard(any(CreditCardRequest.class))).thenReturn(visaCard);

        mockMvc.perform(post("/api/cards")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void createCardReturns400WhenCutoffDayIsOutOfRange() throws Exception {
        CreditCardRequest request = new CreditCardRequest(
                "Visa Oro", null, CardFranchise.VISA, new BigDecimal("5000000"),
                new BigDecimal("0.0250"), 32, 5
        );

        mockMvc.perform(post("/api/cards")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCardReturns400WhenFranchiseIsUnknown() throws Exception {
        String unknownFranchiseBody = """
                {"name": "Visa Oro", "franchise": "NARANJA", "creditLimit": 5000000,
                 "monthlyRate": 0.0250, "cutoffDay": 15, "paymentDueDay": 5}
                """;

        mockMvc.perform(post("/api/cards")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownFranchiseBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCardReturns400WhenMonthlyRateIsNegative() throws Exception {
        CreditCardRequest request = new CreditCardRequest(
                "Visa Oro", null, CardFranchise.VISA, new BigDecimal("5000000"),
                new BigDecimal("-0.01"), 15, 5
        );

        mockMvc.perform(post("/api/cards")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * El endpoint de actualización usa {@code CreditCardUpdateRequest}: franquicia, cupo y saldo
     * enviados por el cliente se ignoran, solo cambian por un movimiento registrado o no cambian
     * en absoluto.
     */
    @Test
    void updateCardIgnoresFranchiseLimitAndBalanceSentByTheClient() throws Exception {
        String bodyWithImmutableFields = """
                {"name": "Renombrada", "bank": "Davivienda", "monthlyRate": 0.0300,
                 "cutoffDay": 20, "paymentDueDay": 10,
                 "franchise": "AMEX", "creditLimit": 99999999, "currentBalance": 0}
                """;
        when(creditCardService.updateCard(eq(1L), any(CreditCardUpdateRequest.class))).thenReturn(visaCard);

        mockMvc.perform(put("/api/cards/1")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithImmutableFields))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.franchise").value("VISA"))
                .andExpect(jsonPath("$.creditLimit").value(5000000))
                .andExpect(jsonPath("$.currentBalance").value(1200000));
    }

    @Test
    void updateCardReturns404WhenCardBelongsToAnotherUser() throws Exception {
        CreditCardUpdateRequest request =
                new CreditCardUpdateRequest("Renombrada", null, new BigDecimal("0.0250"), 15, 5);
        when(creditCardService.updateCard(eq(99L), any(CreditCardUpdateRequest.class)))
                .thenThrow(new CreditCardNotFoundException());

        mockMvc.perform(put("/api/cards/99")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Tarjeta no encontrada"));
    }

    @Test
    void getCardReturns404WhenCardBelongsToAnotherUser() throws Exception {
        when(creditCardService.getCard(99L)).thenThrow(new CreditCardNotFoundException());

        mockMvc.perform(get("/api/cards/99").header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCardReturns204WhenDeletingOwnCard() throws Exception {
        mockMvc.perform(delete("/api/cards/1").header("Authorization", AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCardReturns404WhenCardBelongsToAnotherUser() throws Exception {
        doThrow(new CreditCardNotFoundException()).when(creditCardService).deleteCard(99L);

        mockMvc.perform(delete("/api/cards/99").header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCardsReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isForbidden());
    }
}
