package com.korofin.backend.repository.user;

import com.korofin.backend.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Reserva atómicamente una unidad de la cuota mensual de chat de IA de {@code userId} para
     * {@code period} (ver {@code AiChatService#chat}), evitando un check-then-act separado que
     * sería racy bajo requests concurrentes.
     *
     * <p>Cuando {@code ai_chat_period} todavía no coincide con {@code period} (mes nuevo, o el
     * usuario nunca chateó), el contador se reinicia a {@code 1} para el nuevo período. En otro
     * caso se incrementa — pero solo si eso no superaría {@code limit}: la cláusula {@code WHERE}
     * se resguarda contra actualizar (y devuelve {@code 0} filas afectadas) siempre que el período
     * guardado ya coincida con {@code period} Y {@code ai_chat_used >= limit}, así que como mucho
     * uno de cualquier cantidad de llamadores concurrentes puede ganar la reserva una vez
     * alcanzado el límite.
     *
     * @return {@code 1} si la reserva tuvo éxito, {@code 0} si la cuota ya está agotada para
     *         {@code period}
     */
    @Modifying
    @Query("""
            UPDATE User u
               SET u.aiChatUsed = CASE WHEN u.aiChatPeriod = :period THEN u.aiChatUsed + 1 ELSE 1 END,
                   u.aiChatPeriod = :period
             WHERE u.id = :userId
               AND (u.aiChatPeriod IS NULL OR u.aiChatPeriod <> :period OR u.aiChatUsed < :limit)
            """)
    int reserveAiChatQuota(@Param("userId") Long userId, @Param("period") String period, @Param("limit") int limit);

    /**
     * Decrementa en uno el contador de chat de IA de {@code userId} para {@code period} (con piso
     * en cero), devolviendo una reserva hecha por {@link #reserveAiChatQuota} cuando la llamada al
     * proveedor de IA que protegía finalmente falla. No la llama {@code AiChatService#chat} hoy —
     * ver la nota de diseño en ese método sobre por qué el rollback de la transacción que lo
     * envuelve ya cubre ese caso — pero se mantiene como primitiva explícita, usable de forma
     * independiente.
     */
    @Modifying
    @Query("""
            UPDATE User u
               SET u.aiChatUsed = CASE WHEN u.aiChatUsed > 0 THEN u.aiChatUsed - 1 ELSE 0 END
             WHERE u.id = :userId
               AND u.aiChatPeriod = :period
            """)
    int releaseAiChatQuota(@Param("userId") Long userId, @Param("period") String period);

    /**
     * Ids de todo usuario que se logueó al menos una vez, pensado para {@code InactivityReminderJob}
     * (dominio {@code scheduling}) — un usuario que nunca se logueó no tiene de qué estar
     * "inactivo".
     */
    @Query("SELECT u.id FROM User u WHERE u.lastLoginAt IS NOT NULL")
    List<Long> findAllIdsWithLastLoginNotNull();
}
