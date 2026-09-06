package com.korofin.backend.debt.controller;

import com.korofin.backend.debt.dto.DebtChargeRequest;
import com.korofin.backend.debt.dto.DebtChargeResponse;
import com.korofin.backend.debt.dto.DebtResponse;
import com.korofin.backend.debt.service.DebtChargeService;
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
 * Endpoints REST de los cargos registrados contra una deuda del usuario actual.
 *
 * <p>Igual que los abonos, un cargo es un registro inmutable: solo creación y listado.
 *
 * <p>A diferencia de {@link DebtPaymentController#createPayment}, {@link #createCharge} devuelve
 * el {@link DebtResponse} actualizado en vez del cargo creado — lo que le interesa al cliente
 * después de registrar un cargo es el nuevo saldo de la deuda.
 */
@RestController
@RequestMapping("/api/debts/{debtId}/charges")
public class DebtChargeController {

    private final DebtChargeService debtChargeService;

    public DebtChargeController(DebtChargeService debtChargeService) {
        this.debtChargeService = debtChargeService;
    }

    @GetMapping
    public ResponseEntity<Page<DebtChargeResponse>> getCharges(
            @PathVariable("debtId") Long debtId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(debtChargeService.getCharges(debtId, pageable));
    }

    @PostMapping
    public ResponseEntity<DebtResponse> createCharge(
            @PathVariable("debtId") Long debtId,
            @Valid @RequestBody DebtChargeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(debtChargeService.createCharge(debtId, request));
    }
}
