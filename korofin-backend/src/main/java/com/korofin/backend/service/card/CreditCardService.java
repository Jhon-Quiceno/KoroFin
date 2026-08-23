package com.korofin.backend.service.card;

import com.korofin.backend.dto.card.CreditCardRequest;
import com.korofin.backend.dto.card.CreditCardResponse;
import com.korofin.backend.dto.card.CreditCardUpdateRequest;
import com.korofin.backend.entity.card.CreditCard;
import com.korofin.backend.exception.card.CreditCardNotFoundException;
import com.korofin.backend.mapper.card.CreditCardMapper;
import com.korofin.backend.repository.card.CreditCardRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.SecurityUtils;
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
