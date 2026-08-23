package com.korofin.backend.controller.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korofin.backend.config.JwtProperties;
import com.korofin.backend.config.SecurityConfig;
import com.korofin.backend.dto.ai.CategorizeRequest;
import com.korofin.backend.dto.ai.CategorizeResponse;
import com.korofin.backend.entity.expense.CategoryType;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.JwtService;
import com.korofin.backend.service.ai.AiCategorizationService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiCategorizationController.class)
@Import(SecurityConfig.class)
class AiCategorizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private AiCategorizationService service;

    @MockitoBean
    private JwtProperties jwtProperties;

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
    void categorizeReturns200WithMatchedCategory() throws Exception {
        CategorizeRequest request = new CategorizeRequest("Almuerzo en restaurante", BigDecimal.valueOf(25000), CategoryType.EXPENSE);
        when(service.categorize(eq(request))).thenReturn(new CategorizeResponse(1L, "Comida"));

        mockMvc.perform(post("/api/ai/categorize")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(1))
                .andExpect(jsonPath("$.categoryName").value("Comida"));
    }

    @Test
    void categorizeReturns200WithNullMatchWhenNoneFound() throws Exception {
        CategorizeRequest request = new CategorizeRequest("Pago de arriendo", null, CategoryType.EXPENSE);
        when(service.categorize(eq(request))).thenReturn(new CategorizeResponse(null, null));

        mockMvc.perform(post("/api/ai/categorize")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void categorizeReturns200WithMatchedIncomeCategoryWhenTypeIsIncome() throws Exception {
        CategorizeRequest request = new CategorizeRequest("Pago mensual de nomina", BigDecimal.valueOf(2500000), CategoryType.INCOME);
        when(service.categorize(eq(request))).thenReturn(new CategorizeResponse(3L, "Salario"));

        mockMvc.perform(post("/api/ai/categorize")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(3))
                .andExpect(jsonPath("$.categoryName").value("Salario"));
    }

    @Test
    void categorizeDefaultsToExpenseTypeWhenTypeIsAbsentFromPayload() throws Exception {
        CategorizeRequest expectedRequest = new CategorizeRequest("Mercado semanal", null, CategoryType.EXPENSE);
        when(service.categorize(eq(expectedRequest))).thenReturn(new CategorizeResponse(1L, "Comida"));
        String bodyWithoutType = """
                {"description": "Mercado semanal", "amount": null}
                """;

        mockMvc.perform(post("/api/ai/categorize")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithoutType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(1));
    }

    @Test
    void categorizeReturns400WhenDescriptionIsBlank() throws Exception {
        String invalidBody = """
                {"description": "", "amount": null}
                """;

        mockMvc.perform(post("/api/ai/categorize")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categorizeReturns403WithoutAuthToken() throws Exception {
        CategorizeRequest request = new CategorizeRequest("Almuerzo", null, CategoryType.EXPENSE);

        mockMvc.perform(post("/api/ai/categorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
