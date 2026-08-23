package com.korofin.backend.controller.card;

import com.korofin.backend.dto.card.CardMovementResponse;
import com.korofin.backend.dto.card.CardPaymentRequest;
import com.korofin.backend.dto.card.CardPurchaseRequest;
import com.korofin.backend.dto.card.InstallmentResponse;
import com.korofin.backend.entity.card.CardMovementType;
import com.korofin.backend.service.card.CardMovementService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST de los movimientos registrados contra una tarjeta del usuario actual.
 *
 * <p>Hay dos endpoints de creación separados ({@code /purchases} y {@code /payments}) en vez de
 * uno solo con el tipo en el body: el tipo del movimiento lo decide el servidor según el endpoint
 * llamado, nunca el cliente, y cada uno tiene su propio payload y sus propias reglas (cupo vs.
 * saldo). No existe endpoint de actualización ni de borrado: el ledger de movimientos es
 * inmutable.
 */
@RestController
@RequestMapping("/api/cards/{cardId}")
public class CardMovementController {

    private final CardMovementService cardMovementService;

    public CardMovementController(CardMovementService cardMovementService) {
        this.cardMovementService = cardMovementService;
    }

    @PostMapping("/purchases")
    public ResponseEntity<CardMovementResponse> registerPurchase(
            @PathVariable("cardId") Long cardId,
            @Valid @RequestBody CardPurchaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cardMovementService.registerPurchase(cardId, request));
    }

    @PostMapping("/payments")
    public ResponseEntity<CardMovementResponse> registerPayment(
            @PathVariable("cardId") Long cardId,
            @Valid @RequestBody CardPaymentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cardMovementService.registerPayment(cardId, request));
    }

    @GetMapping("/movements")
    public ResponseEntity<Page<CardMovementResponse>> getMovements(
            @PathVariable("cardId") Long cardId,
            @RequestParam(name = "type", required = false) CardMovementType type,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(cardMovementService.getMovements(cardId, type, pageable));
    }

    @GetMapping("/movements/{movementId}/installments")
    public ResponseEntity<List<InstallmentResponse>> getInstallments(
            @PathVariable("cardId") Long cardId,
            @PathVariable("movementId") Long movementId
    ) {
        return ResponseEntity.ok(cardMovementService.getInstallments(cardId, movementId));
    }
}
