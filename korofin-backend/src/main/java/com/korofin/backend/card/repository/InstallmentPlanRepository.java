package com.korofin.backend.card.repository;

import com.korofin.backend.card.entity.InstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a persistencia de {@link InstallmentPlan}.
 */
public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, Long> {

    /**
     * Busca el plan originado por un movimiento dado (la compra {@code INSTALLMENT_PURCHASE} que
     * lo creó) — lo usa {@code GET /api/cards/{cardId}/movements/{movementId}/installments} para
     * resolver de qué plan listar las cuotas.
     */
    Optional<InstallmentPlan> findByMovement_Id(Long movementId);
}
