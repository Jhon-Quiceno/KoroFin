package com.korofin.backend.recurringpayment.repository;

import com.korofin.backend.recurringpayment.entity.RecurringPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a persistencia de {@link RecurringPayment}, siempre delimitado por dueño.
 */
public interface RecurringPaymentRepository extends JpaRepository<RecurringPayment, Long> {

    Optional<RecurringPayment> findByIdAndUser_Id(Long id, Long userId);

    Page<RecurringPayment> findAllByUser_Id(Long userId, Pageable pageable);

    /**
     * Avanza {@code nextPaymentDate} a {@code newDate} de forma atómica, únicamente si todavía
     * coincide con {@code currentDate} — el guard contra una ejecución duplicada de
     * {@code PATCH /pay} (reintento del cliente tras un timeout, doble clic, dos pestañas
     * abiertas) en la que dos llamadas concurrentes leen el mismo {@code nextPaymentDate}, ambas
     * calculan el mismo {@code newDate}, y ambas crearían un {@link com.korofin.backend.expense.entity.Expense}
     * duplicado si no fuera por este {@code UPDATE} condicional.
     *
     * @return {@code 1} si esta llamada ganó la carrera y avanzó la fecha, {@code 0} si otra
     *         ejecución ya la avanzó primero — quien llama no debe crear el {@code Expense} en
     *         ese caso
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RecurringPayment r SET r.nextPaymentDate = :newDate "
            + "WHERE r.id = :id AND r.nextPaymentDate = :currentDate")
    int advanceNextPaymentDate(
            @Param("id") Long id,
            @Param("currentDate") LocalDate currentDate,
            @Param("newDate") LocalDate newDate
    );

    /**
     * Escaneo cross-user de todo pago recurrente activo cuyo {@code nextPaymentDate} cae dentro
     * de {@code [start, end]}, pensado para {@code PaymentReminderJob} (dominio
     * {@code scheduling}). {@code JOIN FETCH r.user} evita una carga perezosa por fila.
     *
     * <p>Un solo escaneo cross-user, en vez de una consulta por usuario, es lo que mantiene este
     * job barato sin importar cuántos usuarios tenga la plataforma — se apoya en el índice
     * compuesto {@code idx_recurring_payments_active_next_date}.
     */
    @Query("SELECT r FROM RecurringPayment r JOIN FETCH r.user "
            + "WHERE r.active = true AND r.nextPaymentDate BETWEEN :start AND :end")
    List<RecurringPayment> findActiveByNextPaymentDateBetween(
            @Param("start") LocalDate start, @Param("end") LocalDate end
    );
}
