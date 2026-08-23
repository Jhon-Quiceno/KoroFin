package com.korofin.backend.controller.statement;

import com.korofin.backend.dto.statement.StatementConfirmRequest;
import com.korofin.backend.dto.statement.StatementImportResultResponse;
import com.korofin.backend.dto.statement.StatementPreviewResponse;
import com.korofin.backend.service.statement.StatementImportService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints REST para la importación de extractos bancarios: subir un archivo para previsualizar
 * los movimientos detectados, y confirmar la creación de los ingresos/gastos elegidos.
 */
@RestController
@RequestMapping("/api/statement-imports")
public class StatementImportController {

    private final StatementImportService statementImportService;

    public StatementImportController(StatementImportService statementImportService) {
        this.statementImportService = statementImportService;
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StatementPreviewResponse> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password
    ) {
        return ResponseEntity.ok(statementImportService.preview(file, password));
    }

    @PostMapping("/confirm")
    public ResponseEntity<StatementImportResultResponse> confirm(@Valid @RequestBody StatementConfirmRequest request) {
        return ResponseEntity.ok(statementImportService.confirm(request));
    }
}
