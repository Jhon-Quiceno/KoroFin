package com.korofin.backend.notification.service.channel;

import com.korofin.backend.notification.entity.NotificationPreference;
import com.korofin.backend.notification.entity.NotificationType;
import com.korofin.backend.notification.repository.NotificationPreferenceRepository;
import com.korofin.backend.notification.repository.PushTokenRepository;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta la entrega de una notificación a través de todos los canales configurados para su
 * {@link NotificationType}: siempre in-app (vía {@link NotificationService}) cuando el tipo está
 * habilitado para el usuario, más email (vía {@link NotificationSender}) cuando el usuario habilitó
 * la entrega por email, más push (vía {@link PushNotificationSender}) por cada dispositivo
 * registrado.
 *
 * <p>Este es el punto de entrada que deben usar los jobs programados y listeners de eventos de
 * {@code service/scheduling/} — nunca deben llamar a {@link NotificationService#createNotification}
 * directo, porque hacerlo se saltaría el chequeo de preferencias y el abanico a email/push
 * implementado acá.
 *
 * <p>Un usuario sin fila {@link NotificationPreference} todavía (nunca visitó la pantalla de
 * preferencias) se trata como si todo tipo estuviera habilitado y el email deshabilitado, espejando
 * los defaults de columna de {@code V5__create_notification_and_recurring_payment_domains.sql} —
 * esto evita forzar que exista una fila de preferencias antes de poder entregar cualquier
 * notificación.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final NotificationSender notificationSender;
    private final PushTokenRepository pushTokenRepository;
    private final PushNotificationSender pushNotificationSender;

    public NotificationDispatcher(
            NotificationPreferenceRepository notificationPreferenceRepository,
            NotificationService notificationService,
            UserRepository userRepository,
            NotificationSender notificationSender,
            PushTokenRepository pushTokenRepository,
            PushNotificationSender pushNotificationSender
    ) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.notificationSender = notificationSender;
        this.pushTokenRepository = pushTokenRepository;
        this.pushNotificationSender = pushNotificationSender;
    }

    /**
     * Entrega una notificación de {@code type} a {@code userId} por todos los canales habilitados
     * para ese tipo, o se salta por completo si el usuario deshabilitó ese tipo.
     *
     * @param userId    dueño de la notificación
     * @param type      categoría de la notificación
     * @param title     título corto, orientado al usuario (español)
     * @param message   cuerpo completo del mensaje, orientado al usuario (español)
     * @param dedupeKey clave usada para evitar notificaciones in-app duplicadas; se reenvía tal
     *                  cual a {@link NotificationService#createNotification}
     */
    @Transactional
    public void dispatch(Long userId, NotificationType type, String title, String message, String dedupeKey) {
        NotificationPreference preference = notificationPreferenceRepository.findByUser_Id(userId).orElse(null);

        if (!isEnabledFor(preference, type)) {
            log.debug("notification_skipped_by_preference userId={} type={}", userId, type);
            return;
        }

        notificationService.createNotification(userId, type, title, message, dedupeKey);

        boolean emailEnabled = preference != null && preference.isEmailEnabled();
        if (emailEnabled) {
            // Hace falta una carga real (no getReferenceById): los campos del destinatario se leen
            // en un EmailRecipient plano antes de entregarse al sender @Async, y un proxy
            // perezoso lanzaría LazyInitializationException una vez resuelto en ese hilo.
            userRepository.findById(userId).ifPresent(user -> {
                EmailRecipient recipient = new EmailRecipient(user.getId(), user.getEmail(), user.getName());
                notificationSender.send(recipient, title, message);
            });
        }

        // Registrar un dispositivo (POST /api/notifications/push-token) ES el opt-in de push — no
        // existe un toggle de preferencia separado, así que todo token registrado recibe toda
        // notificación despachada que llegue hasta acá.
        pushTokenRepository.findByUser_Id(userId).forEach(pushToken -> {
            PushRecipient recipient = new PushRecipient(userId, pushToken.getExpoPushToken());
            pushNotificationSender.send(recipient, title, message);
        });
    }

    private boolean isEnabledFor(NotificationPreference preference, NotificationType type) {
        if (preference == null) {
            return true;
        }

        return switch (type) {
            case PAYMENT_REMINDER -> preference.isPaymentReminders();
            case OVERSPEND_ALERT -> preference.isOverspendAlerts();
            case WEEKLY_SUMMARY -> preference.isWeeklySummary();
            case INACTIVITY_REMINDER -> preference.isInactivityReminders();
            case CARD_CYCLE_CLOSE -> preference.isCardCycleClose();
            case MONTH_END_PREDICTION, SYSTEM -> true;
        };
    }
}
