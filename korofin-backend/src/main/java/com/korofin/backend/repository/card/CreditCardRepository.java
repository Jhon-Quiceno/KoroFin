package com.korofin.backend.repository.card;

import com.korofin.backend.entity.card.CreditCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a persistencia de {@link CreditCard}, siempre delimitado por dueño.
 *
 * <p>Los cuatro {@code @Modifying} de esta interfaz son el único camino por el que
 * {@link CreditCard#getCurrentBalance()} y {@link CreditCard#getLastCutoffDate()} cambian después
 * de crear la tarjeta. Todos son {@code UPDATE} atómicos y no lectura-y-luego-escritura, para
 * cerrar la carrera de "lost update" entre operaciones concurrentes sobre la misma tarjeta.
 */
public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {

    Optional<CreditCard> findByIdAndUser_Id(Long id, Long userId);

    Page<CreditCard> findAllByUser_Id(Long userId, Pageable pageable);

    /**
     * Incrementa {@code currentBalance} de forma atómica, únicamente si el saldo resultante no
     * supera el cupo de la tarjeta ({@code creditLimit}). El {@code WHERE} convierte el
     * incremento y la validación de cupo en una sola operación de base de datos, cerrando la
     * misma carrera que {@code DebtRepository#decrementRemainingAmount} cierra para deudas.
     *
     * <p>{@code clearAutomatically = true} desvincula el contexto de persistencia después del
     * {@code UPDATE} masivo, para que la relectura posterior de la tarjeta (necesaria para
     * devolver {@code cardBalanceAfter} en la respuesta) vaya a la base de datos en vez de
     * devolver la entidad obsoleta del caché de primer nivel.
     *
     * @return {@code 1} si el incremento se aplicó, {@code 0} si {@code amount} haría que el
     *         saldo superara el cupo — quien llama debe tratar {@code 0} como una compra
     *         rechazada y no debe persistir el {@code CardMovement}
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CreditCard c SET c.currentBalance = c.currentBalance + :amount "
            + "WHERE c.id = :id AND c.currentBalance + :amount <= c.creditLimit")
    int incrementBalanceWithinLimit(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * Incrementa {@code currentBalance} de forma atómica, sin validar el cupo. Pensado para
     * movimientos generados por el sistema que siempre deben aplicarse — lo usa
     * {@code CycleCloseService} para materializar el interés agregado del cierre de ciclo, que
     * nunca debe rechazarse por falta de cupo (el interés se devengó igual).
     *
     * @return {@code 1} si el incremento se aplicó, {@code 0} si la tarjeta ya no existe
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CreditCard c SET c.currentBalance = c.currentBalance + :amount WHERE c.id = :id")
    int incrementBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * Decrementa {@code currentBalance} de forma atómica, únicamente si el saldo actual alcanza
     * para cubrir {@code amount}. Espejo de {@code DebtRepository#decrementRemainingAmount}, lo
     * usa {@code CardMovementService#registerPayment}.
     *
     * @return {@code 1} si el decremento se aplicó, {@code 0} si {@code amount} supera el saldo
     *         actual — quien llama debe tratar {@code 0} como un pago rechazado y no debe
     *         persistir el {@code CardMovement}
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CreditCard c SET c.currentBalance = c.currentBalance - :amount "
            + "WHERE c.id = :id AND c.currentBalance >= :amount")
    int decrementBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * Suma {@code currentBalance} de todas las tarjetas del usuario, pensada para el dominio
     * {@code analysis} de una fase posterior (razón de endeudamiento), junto a
     * {@code DebtRepository#sumRemainingAmountByUser}.
     */
    @Query("SELECT COALESCE(SUM(c.currentBalance), 0) FROM CreditCard c WHERE c.user.id = :userId")
    BigDecimal sumCurrentBalanceByUser(@Param("userId") Long userId);

    /**
     * Guard atómico de idempotencia del cierre de ciclo: marca la tarjeta como cerrada hasta
     * {@code closeDate} únicamente si todavía no lo estaba. Corre dentro de la misma transacción
     * en la que {@code CycleCloseService#closeCycle} crea el movimiento {@code INTEREST}, así que
     * si el cierre se dispara dos veces el mismo día (o dos instancias concurrentes procesan la
     * misma tarjeta), la segunda ejecución ve {@code 0} filas actualizadas y debe abortar sin
     * tocar nada más — es lo que evita duplicar el interés materializado.
     *
     * @return {@code 1} si el guard se aplicó (la tarjeta queda marcada como cerrada hasta
     *         {@code closeDate}), {@code 0} si ya estaba cerrada para esa fecha o una posterior
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CreditCard c SET c.lastCutoffDate = :closeDate "
            + "WHERE c.id = :id AND (c.lastCutoffDate IS NULL OR c.lastCutoffDate < :closeDate)")
    int markCutoffClosed(@Param("id") Long id, @Param("closeDate") LocalDate closeDate);

    /**
     * Candidatas a cierre de ciclo: toda tarjeta que nunca cerró un ciclo o cuyo último cierre es
     * anterior a {@code today}. Consulta amplia y cross-user que deliberadamente NO filtra por
     * "{@code cutoffDay} == día de hoy": ese filtro perdería el ciclo completo si el servidor
     * estuvo caído justo el día del corte. El filtro fino (¿el corte de esta tarjeta ya venció?)
     * lo resuelve quien la consuma, porque {@code cutoffDay} es un día del mes (1-31) y no una
     * fecha.
     *
     * <p>Queda lista para el {@code CardCycleCloseJob} de la fase de scheduling, que todavía no
     * existe; {@code CycleCloseService} es invocable por tarjeta desde ya.
     */
    @Query("SELECT c FROM CreditCard c WHERE c.lastCutoffDate IS NULL OR c.lastCutoffDate < :today")
    List<CreditCard> findCardsPendingCycleClose(@Param("today") LocalDate today);
}
