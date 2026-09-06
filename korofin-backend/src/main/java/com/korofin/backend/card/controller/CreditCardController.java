package com.korofin.backend.card.controller;

import com.korofin.backend.card.dto.CreditCardRequest;
import com.korofin.backend.card.dto.CreditCardResponse;
import com.korofin.backend.card.dto.CreditCardUpdateRequest;
import com.korofin.backend.card.service.CreditCardService;
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
 * Endpoints REST de las tarjetas de crédito del usuario actual.
 *
 * <p>{@code PUT} recibe un {@link CreditCardUpdateRequest} y no un {@link CreditCardRequest}: la
 * franquicia, el cupo y el saldo son inmutables desde este endpoint (ver el Javadoc del DTO).
 */
@RestController
@RequestMapping("/api/cards")
public class CreditCardController {

    private final CreditCardService creditCardService;

    public CreditCardController(CreditCardService creditCardService) {
        this.creditCardService = creditCardService;
    }

    @GetMapping
    public ResponseEntity<Page<CreditCardResponse>> getCards(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(creditCardService.getCards(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreditCardResponse> getCard(@PathVariable("id") Long cardId) {
        return ResponseEntity.ok(creditCardService.getCard(cardId));
    }

    @PostMapping
    public ResponseEntity<CreditCardResponse> createCard(@Valid @RequestBody CreditCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(creditCardService.createCard(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CreditCardResponse> updateCard(
            @PathVariable("id") Long cardId,
            @Valid @RequestBody CreditCardUpdateRequest request
    ) {
        return ResponseEntity.ok(creditCardService.updateCard(cardId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCard(@PathVariable("id") Long cardId) {
        creditCardService.deleteCard(cardId);
        return ResponseEntity.noContent().build();
    }
}
