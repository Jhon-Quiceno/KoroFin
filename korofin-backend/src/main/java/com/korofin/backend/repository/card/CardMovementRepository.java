package com.korofin.backend.repository.card;

import com.korofin.backend.entity.card.CardMovement;
import com.korofin.backend.entity.card.CardMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a persistencia de {@link CardMovement}.
 *
 * <p>La pertenencia de la {@link com.korofin.backend.entity.card.CreditCard} padre la valida
 * {@code CardMovementService} antes de ejecutar cualquier consulta acá, así que no hace falta un
 * filtro adicional por usuario en este nivel (espejo de {@code DebtChargeRepository}/
 * {@code DebtPaymentRepository}).
 */
public interface CardMovementRepository extends JpaRepository<CardMovement, Long> {

    Page<CardMovement> findAllByCard_Id(Long cardId, Pageable pageable);

    /**
     * Variante filtrada por {@link CardMovementType}, que respalda
     * {@code GET /api/cards/{cardId}/movements?type=} cuando el cliente pide un tipo específico.
     */
    Page<CardMovement> findAllByCard_IdAndType(Long cardId, CardMovementType type, Pageable pageable);
}
