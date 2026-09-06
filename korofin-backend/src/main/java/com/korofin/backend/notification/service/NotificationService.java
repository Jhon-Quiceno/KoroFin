package com.korofin.backend.notification.service;

import com.korofin.backend.notification.dto.NotificationPreferenceRequest;
import com.korofin.backend.notification.dto.NotificationPreferenceResponse;
import com.korofin.backend.notification.dto.NotificationResponse;
import com.korofin.backend.notification.dto.PushTokenRequest;
import com.korofin.backend.notification.entity.Notification;
import com.korofin.backend.notification.entity.NotificationPreference;
import com.korofin.backend.notification.entity.NotificationType;
import com.korofin.backend.notification.entity.PushToken;
import com.korofin.backend.common.exception.ResourceNotFoundException;
import com.korofin.backend.notification.mapper.NotificationMapper;
import com.korofin.backend.notification.mapper.NotificationPreferenceMapper;
import com.korofin.backend.notification.repository.NotificationPreferenceRepository;
import com.korofin.backend.notification.repository.NotificationRepository;
import com.korofin.backend.notification.repository.PushTokenRepository;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Lógica de negocio de las {@link Notification} y {@link NotificationPreference} del usuario
 * actual.
 *
 * <p>Toda operación de lectura/escritura resuelve al llamador vía
 * {@link SecurityUtils#getCurrentUserId()} y se delimita estrictamente a ese usuario, siguiendo el
 * mismo patrón de pertenencia que {@code ExpenseService}. {@link #createNotification} también se
 * llama con un {@code userId} explícito (no el llamador actual) porque está pensado para
 * invocarse desde {@code NotificationDispatcher}/jobs programados de {@code service/scheduling/}
 * actuando en nombre de usuarios arbitrarios, no desde una request autenticada.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationPreferenceMapper notificationPreferenceMapper;
    private final PushTokenRepository pushTokenRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            UserRepository userRepository,
            NotificationMapper notificationMapper,
            NotificationPreferenceMapper notificationPreferenceMapper,
            PushTokenRepository pushTokenRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.userRepository = userRepository;
        this.notificationMapper = notificationMapper;
        this.notificationPreferenceMapper = notificationPreferenceMapper;
        this.pushTokenRepository = pushTokenRepository;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable)
                .map(notificationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        return notificationRepository.countByUser_IdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Notification notification = notificationRepository.findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }

        return notificationMapper.toResponse(notification);
    }

    @Transactional
    public void markAllAsRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationRepository.markAllAsReadForUser(userId);
    }

    /**
     * Crea una notificación para {@code userId}, saltándose en silencio cuando {@code dedupeKey}
     * no es nulo y ya existe una notificación con el mismo {@code (userId, dedupeKey)}.
     *
     * <p>El chequeo de existencia previo ({@link NotificationRepository#existsByUser_IdAndDedupeKey})
     * evita un intento de insert innecesario en el caso común; el {@code saveAndFlush} +
     * la captura de {@link DataIntegrityViolationException} maneja la carrera en la que dos
     * llamadas concurrentes (por ejemplo dos ejecuciones de un job que se solapan) pasan el
     * chequeo previo antes de que ninguna haga commit — el índice único parcial
     * {@code uk_notifications_user_dedupe_key} (ver
     * {@code V5__create_notification_and_recurring_payment_domains.sql}) rechaza el segundo
     * insert, y ese rechazo se trata como un no-op idempotente en vez de un error.
     *
     * <p>Corre en su propia transacción {@link Propagation#REQUIRES_NEW}, como defensa en
     * profundidad: una {@link DataIntegrityViolationException} de la carrera de dedupe aborta solo
     * la transacción de este método, nunca la de quien llama, así que llamadores que invocan esto
     * dentro de una transacción más grande (por ejemplo {@code OverspendAlertListener}) nunca
     * pueden ver su propio trabajo revertido por una colisión de dedupe acá.
     *
     * @param userId    dueño de la notificación
     * @param type      categoría de la notificación
     * @param title     título corto, orientado al usuario (español)
     * @param message   cuerpo completo del mensaje, orientado al usuario (español)
     * @param dedupeKey clave usada para evitar notificaciones duplicadas para el mismo
     *                  (usuario, target, período); {@code null} deshabilita la deduplicación
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(Long userId, NotificationType type, String title, String message, String dedupeKey) {
        if (dedupeKey != null && notificationRepository.existsByUser_IdAndDedupeKey(userId, dedupeKey)) {
            log.debug("skip_duplicate_notification userId={} type={} dedupeKey={}", userId, type, dedupeKey);
            return;
        }

        Notification notification = new Notification();
        notification.setUser(userRepository.getReferenceById(userId));
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setDedupeKey(dedupeKey);

        try {
            notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException ex) {
            log.debug(
                    "skip_duplicate_notification_race userId={} type={} dedupeKey={}", userId, type, dedupeKey
            );
        }
    }

    @Transactional
    public NotificationPreferenceResponse getPreferences() {
        Long userId = SecurityUtils.getCurrentUserId();
        return notificationPreferenceMapper.toResponse(findOrCreatePreference(userId));
    }

    @Transactional
    public NotificationPreferenceResponse updatePreferences(NotificationPreferenceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        NotificationPreference preference = findOrCreatePreference(userId);

        notificationPreferenceMapper.updateEntityFromRequest(request, preference);

        return notificationPreferenceMapper.toResponse(notificationPreferenceRepository.save(preference));
    }

    /**
     * Registra (o actualiza) un token de Expo push de uno de los dispositivos del usuario actual.
     *
     * <p>Hace upsert por {@code (user, deviceId)} en vez de insertar siempre: el mismo dispositivo
     * reinstalando la app o con su token rotado por Expo debe sobrescribir la fila existente, no
     * acumular duplicados (reforzado a nivel de base de datos por
     * {@code uk_push_tokens_user_device}).
     *
     * @param request el token actual del dispositivo y un identificador estable del dispositivo
     */
    @Transactional
    public void registerPushToken(PushTokenRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        PushToken pushToken = pushTokenRepository.findByUser_IdAndDeviceId(userId, request.deviceId())
                .orElseGet(() -> {
                    PushToken created = new PushToken();
                    created.setUser(userRepository.getReferenceById(userId));
                    created.setDeviceId(request.deviceId());
                    return created;
                });

        pushToken.setExpoPushToken(request.expoPushToken());
        pushTokenRepository.save(pushToken);
    }

    /**
     * Idempotente por diseño: borrar un {@code deviceId} que nunca se registró (permiso denegado,
     * corriendo en Expo Go donde push remoto no está disponible, etc.) igual devuelve éxito — quien
     * llama (flujo de logout) no puede saber de antemano si algún token se registró.
     *
     * @param deviceId el identificador de dispositivo a remover
     */
    @Transactional
    public void unregisterPushToken(String deviceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        pushTokenRepository.deleteByUser_IdAndDeviceId(userId, deviceId);
    }

    /**
     * Devuelve las preferencias persistidas del usuario, creando una fila con los mismos defaults
     * que las columnas de la base de datos (ver
     * {@code V5__create_notification_and_recurring_payment_domains.sql}) la primera vez que se
     * piden, así que tanto {@link #getPreferences()} como {@link #updatePreferences} siempre
     * tienen una fila para leer/mutar.
     */
    private NotificationPreference findOrCreatePreference(Long userId) {
        return notificationPreferenceRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    NotificationPreference defaults = new NotificationPreference();
                    defaults.setUser(userRepository.getReferenceById(userId));
                    defaults.setPaymentReminders(true);
                    defaults.setOverspendAlerts(true);
                    defaults.setWeeklySummary(true);
                    defaults.setInactivityReminders(true);
                    defaults.setCardCycleClose(true);
                    defaults.setEmailEnabled(false);
                    return notificationPreferenceRepository.save(defaults);
                });
    }
}
