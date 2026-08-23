package com.korofin.backend.statement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korofin.backend.common.config.JwtProperties;
import com.korofin.backend.common.config.SecurityConfig;
import com.korofin.backend.statement.dto.ImportConfirmRow;
import com.korofin.backend.statement.dto.ImportPreviewRow;
import com.korofin.backend.statement.dto.MovementType;
import com.korofin.backend.statement.dto.StatementConfirmRequest;
import com.korofin.backend.statement.dto.StatementImportResultResponse;
import com.korofin.backend.statement.dto.StatementPreviewResponse;
import com.korofin.backend.expense.entity.PaymentMethodType;
import com.korofin.backend.statement.exception.EmptyStatementTextException;
import com.korofin.backend.statement.exception.UnsupportedStatementFileException;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.JwtService;
import com.korofin.backend.statement.service.StatementImportService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatementImportController.class)
@Import(SecurityConfig.class)
class StatementImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private StatementImportService statementImportService;

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
    void previewReturns200WithExtractedRows() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "extracto.csv", "text/csv", "fecha,monto\n".getBytes()
        );
        StatementPreviewResponse response = new StatementPreviewResponse(
                List.of(new ImportPreviewRow(
                        LocalDate.of(2026, 6, 5), "Supermercado", BigDecimal.valueOf(85000),
                        MovementType.EXPENSE, false, 2L, "Alimentación"
                )),
                1, 0
        );
        when(statementImportService.preview(any(), isNull())).thenReturn(response);

        mockMvc.perform(multipart("/api/statement-imports/preview")
                        .file(file)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.duplicateRows").value(0))
                .andExpect(jsonPath("$.rows[0].description").value("Supermercado"));
    }

    @Test
    void previewPassesPasswordParameterThrough() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "extracto.pdf", "application/pdf", "contenido".getBytes()
        );
        when(statementImportService.preview(any(), eq("secreta"))).thenReturn(
                new StatementPreviewResponse(List.of(), 0, 0)
        );

        mockMvc.perform(multipart("/api/statement-imports/preview")
                        .file(file)
                        .param("password", "secreta")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk());
    }

    @Test
    void previewReturns400ForUnsupportedFileFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "extracto.docx", "application/msword", "contenido".getBytes()
        );
        when(statementImportService.preview(any(), isNull()))
                .thenThrow(new UnsupportedStatementFileException("El formato del archivo no es compatible."));

        mockMvc.perform(multipart("/api/statement-imports/preview")
                        .file(file)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewReturns422WhenExtractedTextIsEmpty() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "extracto.pdf", "application/pdf", "contenido".getBytes()
        );
        when(statementImportService.preview(any(), isNull()))
                .thenThrow(new EmptyStatementTextException("No se encontró texto en el archivo."));

        mockMvc.perform(multipart("/api/statement-imports/preview")
                        .file(file)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void previewReturns403WithoutAuthToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "extracto.csv", "text/csv", "x".getBytes());

        mockMvc.perform(multipart("/api/statement-imports/preview").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmReturns200WithCreatedCount() throws Exception {
        StatementConfirmRequest request = new StatementConfirmRequest(List.of(
                new ImportConfirmRow(
                        MovementType.EXPENSE, BigDecimal.valueOf(85000), LocalDate.of(2026, 6, 5),
                        "Supermercado", 2L, PaymentMethodType.DEBIT_CARD
                )
        ));
        when(statementImportService.confirm(any())).thenReturn(new StatementImportResultResponse(1));

        mockMvc.perform(post("/api/statement-imports/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1));
    }

    @Test
    void confirmReturns400WhenRowsAreEmpty() throws Exception {
        String invalidBody = """
                {"rows": []}
                """;

        mockMvc.perform(post("/api/statement-imports/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmReturns400WhenAmountIsMissing() throws Exception {
        String invalidBody = """
                {"rows": [{"movementType": "EXPENSE", "date": "2026-06-05", "description": "X"}]}
                """;

        mockMvc.perform(post("/api/statement-imports/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmReturns403WithoutAuthToken() throws Exception {
        String body = """
                {"rows": [{"movementType": "EXPENSE", "amount": 1000, "date": "2026-06-05", "description": "X"}]}
                """;

        mockMvc.perform(post("/api/statement-imports/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
