package com.korofin.backend.controller.debt;

import com.korofin.backend.dto.debt.DebtRequest;
import com.korofin.backend.dto.debt.DebtResponse;
import com.korofin.backend.dto.debt.DebtUpdateRequest;
import com.korofin.backend.service.debt.DebtService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de las deudas del usuario actual.
 *
 * <p>{@code PUT} recibe un {@link DebtUpdateRequest} y no un {@link DebtRequest}: el monto total
 * y el saldo restante son inmutables desde este endpoint (ver el Javadoc del DTO).
 */
@RestController
@RequestMapping("/api/debts")
public class DebtController {

    private final DebtService debtService;

    public DebtController(DebtService debtService) {
        this.debtService = debtService;
    }

    @GetMapping
    public ResponseEntity<Page<DebtResponse>> getDebts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(debtService.getDebts(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DebtResponse> getDebt(@PathVariable("id") Long debtId) {
        return ResponseEntity.ok(debtService.getDebt(debtId));
    }

    @PostMapping
    public ResponseEntity<DebtResponse> createDebt(@Valid @RequestBody DebtRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(debtService.createDebt(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DebtResponse> updateDebt(
            @PathVariable("id") Long debtId,
            @Valid @RequestBody DebtUpdateRequest request
    ) {
        return ResponseEntity.ok(debtService.updateDebt(debtId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDebt(@PathVariable("id") Long debtId) {
        debtService.deleteDebt(debtId);
        return ResponseEntity.noContent().build();
    }
}
