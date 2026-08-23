package com.korofin.backend.repository.notification;

import com.korofin.backend.entity.notification.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a persistencia de {@link NotificationPreference}, siempre delimitado por dueño.
 */
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUser_Id(Long userId);
}
