package com.korofin.backend.controller.debt;

import com.korofin.backend.dto.debt.DebtPaymentRequest;
import com.korofin.backend.dto.debt.DebtPaymentResponse;
import com.korofin.backend.service.debt.DebtPaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de los abonos registrados contra una deuda del usuario actual.
 *
 * <p>Solo hay creación y listado: un abono es un registro inmutable del ledger de la deuda, así
 * que nunca se exponen endpoints de actualización ni de borrado — corregir un abono equivocado se
 * hace registrando el cargo inverso.
 */
@RestController
@RequestMapping("/api/debts/{debtId}/payments")
public class DebtPaymentController {

    private final DebtPaymentService debtPaymentService;

    public DebtPaymentController(DebtPaymentService debtPaymentService) {
        this.debtPaymentService = debtPaymentService;
    }

    @GetMapping
    public ResponseEntity<Page<DebtPaymentResponse>> getPayments(
            @PathVariable("debtId") Long debtId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(debtPaymentService.getPayments(debtId, pageable));
    }

    @PostMapping
    public ResponseEntity<DebtPaymentResponse> createPayment(
            @PathVariable("debtId") Long debtId,
            @Valid @RequestBody DebtPaymentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(debtPaymentService.createPayment(debtId, request));
    }
}
