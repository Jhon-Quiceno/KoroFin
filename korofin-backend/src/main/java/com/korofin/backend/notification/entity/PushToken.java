package com.korofin.backend.notification.entity;

import com.korofin.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Un token de Expo push registrado por uno de los dispositivos móviles de un {@link User}.
 *
 * <p>Una fila por {@code (user, device)} — ver {@code uk_push_tokens_user_device} en
 * {@code V5__create_notification_and_recurring_payment_domains.sql} — así que volver a registrar
 * el mismo dispositivo (reinstalación, token de Expo rotado) sobrescribe {@link #expoPushToken} en
 * vez de acumular filas duplicadas. Un usuario sin fila acá nunca optó por push en ningún
 * dispositivo; {@code NotificationDispatcher} envía un push por token registrado, así que cero
 * filas significa cero notificaciones push, en silencio.
 */
@Entity
@Table(name = "push_tokens")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expo_push_token", nullable = false, length = 200)
    private String expoPushToken;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
