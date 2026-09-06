package com.korofin.backend.card.repository;

import com.korofin.backend.card.entity.Installment;
import com.korofin.backend.card.entity.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Acceso a persistencia de {@link Installment}.
 */
public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    /**
     * Cuotas de un plan ordenadas por número, que respalda
     * {@code GET /api/cards/{cardId}/movements/{movementId}/installments}.
     */
    List<Installment> findAllByPlan_IdOrderByNumber(Long planId);

    /**
     * Cuotas pendientes ya vencidas hasta la fecha dada, de cualquier plan de cualquier compra de
     * la tarjeta indicada. La usa {@code CycleCloseService} para calcular el interés agregado del
     * cierre de ciclo.
     *
     * <p>Esta misma consulta resuelve el "catch-up": si el cierre no corrió el día exacto del
     * corte, en la próxima corrida las cuotas siguen {@code PENDING} con {@code dueDate} en el
     * pasado y se facturan igual, sin perder el ciclo.
     *
     * <p>El nombre necesita el salto intermedio por {@code movement} porque
     * {@link com.korofin.backend.card.entity.InstallmentPlan} no tiene un campo {@code card}
     * directo, solo {@code movement.card}.
     */
    List<Installment> findByPlan_Movement_Card_IdAndStatusAndDueDateLessThanEqual(
            Long cardId,
            InstallmentStatus status,
            LocalDate dueDate
    );
}
