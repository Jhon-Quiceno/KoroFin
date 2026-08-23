package com.korofin.backend.repository.notification;

import com.korofin.backend.entity.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Acceso a persistencia de {@link Notification}, siempre delimitado por dueño.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

    Page<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUser_IdAndReadFalse(Long userId);

    /**
     * Respalda el chequeo de dedupe previo de {@code NotificationService#createNotification}. El
     * guard definitivo es el índice único parcial {@code uk_notifications_user_dedupe_key} (ver
     * {@code V5__create_notification_and_recurring_payment_domains.sql}); este método solo evita
     * un intento de insert innecesario en el caso común (sin carrera).
     */
    boolean existsByUser_IdAndDedupeKey(Long userId, String dedupeKey);

    /**
     * Marca en bloque toda notificación no leída de {@code userId} como leída en una sola
     * sentencia, en vez de cargar y guardar cada fila individualmente.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP "
            + "WHERE n.user.id = :userId AND n.read = false")
    int markAllAsReadForUser(@Param("userId") Long userId);
}
