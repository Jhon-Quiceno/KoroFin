package com.korofin.backend.card.service;

import com.korofin.backend.card.dto.CreditCardRequest;
import com.korofin.backend.card.dto.CreditCardResponse;
import com.korofin.backend.card.dto.CreditCardUpdateRequest;
import com.korofin.backend.card.entity.CreditCard;
import com.korofin.backend.card.exception.CreditCardNotFoundException;
import com.korofin.backend.card.mapper.CreditCardMapper;
import com.korofin.backend.card.repository.CreditCardRepository;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Lógica de negocio para administrar las tarjetas de crédito del usuario actual.
 *
 * <p>Toda operación resuelve al llamador vía {@link SecurityUtils#getCurrentUserId()} y limita
 * lecturas/escrituras estrictamente a ese usuario. Las mutaciones sobre una tarjeta ajena lanzan
 * {@link CreditCardNotFoundException} (404) en vez de 403, para no filtrar su existencia a quien
 * no es su dueño — mismo criterio que {@code DebtService}.
 *
 * <p>{@link #updateCard} deliberadamente no puede cambiar {@code franchise}, {@code creditLimit}
 * ni {@code currentBalance} — ver el Javadoc de {@link CreditCardUpdateRequest}. El único lugar
 * donde {@code currentBalance} cambia después de la creación son los {@code UPDATE} atómicos de
 * {@code CreditCardRepository}, disparados por un movimiento del ledger.
 */
@Service
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final UserRepository userRepository;
    private final CreditCardMapper creditCardMapper;

    public CreditCardService(
            CreditCardRepository creditCardRepository,
            UserRepository userRepository,
            CreditCardMapper creditCardMapper
    ) {
        this.creditCardRepository = creditCardRepository;
        this.userRepository = userRepository;
        this.creditCardMapper = creditCardMapper;
    }

    @Transactional(readOnly = true)
    public Page<CreditCardResponse> getCards(Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        return creditCardRepository.findAllByUser_Id(userId, pageable)
                .map(creditCardMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CreditCardResponse getCard(Long cardId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return creditCardMapper.toResponse(findOwnedCard(cardId, userId));
    }

    @Transactional
    public CreditCardResponse createCard(CreditCardRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        CreditCard card = creditCardMapper.toEntity(request);
        card.setUser(userRepository.getReferenceById(userId));
        // Una tarjeta nueva arranca sin deuda: el saldo lo construyen los movimientos.
        card.setCurrentBalance(BigDecimal.ZERO);

        CreditCard savedCard = creditCardRepository.save(card);
        return creditCardMapper.toResponse(savedCard);
    }

    @Transactional
    public CreditCardResponse updateCard(Long cardId, CreditCardUpdateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        CreditCard card = findOwnedCard(cardId, userId);

        creditCardMapper.updateEntityFromRequest(request, card);

        CreditCard updatedCard = creditCardRepository.save(card);
        return creditCardMapper.toResponse(updatedCard);
    }

    @Transactional
    public void deleteCard(Long cardId) {
        Long userId = SecurityUtils.getCurrentUserId();
        CreditCard card = findOwnedCard(cardId, userId);
        creditCardRepository.delete(card);
    }

    private CreditCard findOwnedCard(Long cardId, Long userId) {
        return creditCardRepository.findByIdAndUser_Id(cardId, userId)
                .orElseThrow(CreditCardNotFoundException::new);
    }
}
