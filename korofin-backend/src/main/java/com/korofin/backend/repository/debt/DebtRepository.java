package com.korofin.backend.repository.debt;

import com.korofin.backend.entity.debt.Debt;
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
 * Acceso a persistencia de {@link Debt}, siempre delimitado por dueño.
 *
 * <p>Los dos {@code @Modifying} de esta interfaz son el único camino por el que
 * {@link Debt#getRemainingAmount()} cambia después de crear la deuda. Ambos son {@code UPDATE}
 * atómicos y no lectura-y-luego-escritura, precisamente para cerrar la carrera de "lost update"
 * entre dos operaciones concurrentes sobre la misma deuda.
 */
public interface DebtRepository extends JpaRepository<Debt, Long> {

    Optional<Debt> findByIdAndUser_Id(Long id, Long userId);

    Page<Debt> findAllByUser_Id(Long userId, Pageable pageable);

    /**
     * Variante sin paginar de {@link #findAllByUser_Id(Long, Pageable)}, pensada para los
     * dominios de fases posteriores que necesitan listar todas las deudas del usuario de una vez
     * (por ejemplo el contexto financiero que se le pasa a la IA).
     */
    List<Debt> findAllByUser_Id(Long userId);

    /**
     * Decrementa {@code remainingAmount} de forma atómica, con la validación de "el abono no
     * supera el saldo restante" incorporada en el {@code WHERE}, de modo que el decremento y la
     * validación ocurran como una sola operación de base de datos en vez de leer-y-luego-escribir.
     *
     * <p>Esto cierra la carrera de "lost update" en la que dos
     * {@code DebtPaymentService#createPayment} concurrentes leen el mismo
     * {@code remainingAmount}, ambos pasan una validación en memoria, y el segundo {@code save()}
     * pisa silenciosamente el decremento del primero. El {@code CHECK (remaining_amount >= 0)} de
     * la migración no atrapa ese caso, porque cada valor final calculado por separado puede
     * satisfacerlo individualmente.
     *
     * <p>{@code clearAutomatically = true} desvincula el contexto de persistencia después del
     * {@code UPDATE} masivo, para que cualquier lectura posterior de la {@link Debt} vaya a la
     * base de datos en vez de devolver la entidad obsoleta del caché de primer nivel, y para que
     * el dirty-checking de Hibernate no vuelva a escribir después un {@code remainingAmount}
     * calculado en Java (que reintroduciría la misma carrera que este {@code UPDATE} existe para
     * evitar).
     *
     * @return {@code 1} si el decremento se aplicó, {@code 0} si {@code amount} supera el saldo
     *         restante actual de la deuda — quien llama debe tratar {@code 0} como un abono
     *         rechazado y no debe persistir el {@code DebtPayment}
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Debt d SET d.remainingAmount = d.remainingAmount - :amount "
            + "WHERE d.id = :id AND d.remainingAmount >= :amount")
    int decrementRemainingAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * Incrementa {@code remainingAmount} de forma atómica, el espejo de
     * {@link #decrementRemainingAmount} que usa {@code DebtChargeService#createCharge} al
     * registrar un cargo contra la deuda.
     *
     * <p>A diferencia del decremento, este no tiene cláusula de guard sobre el monto:
     * {@link Debt} no tiene un campo de cupo máximo, así que no hay tope que validar. El
     * {@code WHERE d.id = :id} sigue siendo relevante — devuelve {@code 0} si la deuda fue
     * borrada de forma concurrente entre la verificación de pertenencia y este {@code UPDATE}.
     *
     * <p>{@code clearAutomatically = true} por la misma razón que en
     * {@link #decrementRemainingAmount}: quien llama tiene que releer la {@link Debt} para
     * devolver el {@code remainingAmount} actualizado en la respuesta, y esa relectura debe ir a
     * la base de datos.
     *
     * @return {@code 1} si el incremento se aplicó, {@code 0} si la deuda ya no existe
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Debt d SET d.remainingAmount = d.remainingAmount + :amount WHERE d.id = :id")
    int incrementRemainingAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * Suma {@code remainingAmount} de todas las deudas del usuario, pensada para el dominio
     * {@code analysis} de una fase posterior (razón de endeudamiento). Una deuda ya saldada
     * aporta {@code 0}, que es el comportamiento correcto sin necesidad de un filtro aparte (ver
     * el Javadoc de {@link Debt} sobre por qué no hay bandera {@code isActive}).
     */
    @Query("SELECT COALESCE(SUM(d.remainingAmount), 0) FROM Debt d WHERE d.user.id = :userId")
    BigDecimal sumRemainingAmountByUser(@Param("userId") Long userId);

    /**
     * Escaneo cross-user de deudas con saldo positivo cuya {@code dueDate} cae dentro de
     * {@code [start, end]}, pensado para el job de recordatorio de pagos de la fase de
     * scheduling. {@code JOIN FETCH d.user} evita una carga perezosa por fila, y excluir
     * {@code remainingAmount <= 0} salta las deudas ya saldadas.
     *
     * <p>Se apoya en el índice compuesto {@code idx_debts_user_due_date} de
     * {@code V3__add_debt_and_card_domain.sql}.
     */
    @Query("SELECT d FROM Debt d JOIN FETCH d.user "
            + "WHERE d.remainingAmount > 0 AND d.dueDate BETWEEN :start AND :end")
    List<Debt> findWithBalanceByDueDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
