package com.korofin.backend.ai.controller;

import com.korofin.backend.ai.dto.ReceiptExtraction;
import com.korofin.backend.ai.dto.ReceiptScanRequest;
import com.korofin.backend.common.security.SecurityUtils;
import com.korofin.backend.ai.service.ReceiptExtractionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Captura nativa de recibos desde la app móvil: expone
 * {@link ReceiptExtractionService#extractFromImage} detrás de JWT estándar. La app solo confirma
 * la extracción llamando a los endpoints ya existentes de gastos/ingresos
 * ({@code POST /api/expenses}, {@code POST /api/incomes}) — este endpoint no crea ningún
 * movimiento por sí mismo, solo lee la imagen.
 */
@RestController
@RequestMapping("/api/receipts")
public class ReceiptScanController {

    private final ReceiptExtractionService receiptExtractionService;

    public ReceiptScanController(ReceiptExtractionService receiptExtractionService) {
        this.receiptExtractionService = receiptExtractionService;
    }

    @PostMapping("/scan")
    public ResponseEntity<ReceiptExtraction> scan(@Valid @RequestBody ReceiptScanRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        ReceiptExtraction extraction = receiptExtractionService.extractFromImage(userId, request.imageDataUri());
        return ResponseEntity.ok(extraction);
    }
}
