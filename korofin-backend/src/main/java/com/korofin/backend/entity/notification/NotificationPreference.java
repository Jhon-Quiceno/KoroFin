package com.korofin.backend.entity.notification;

import com.korofin.backend.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Toggles por {@link User} que controlan qué categorías de {@link NotificationType} se entregan y
 * si además se entregan por email.
 *
 * <p>Como mucho una fila por usuario ({@code uk_notification_preferences_user} en
 * {@code V5__create_notification_and_recurring_payment_domains.sql}). {@code NotificationService}
 * crea esta fila de forma perezosa, con los mismos defaults que las columnas de la base de datos,
 * la primera vez que se leen o actualizan las preferencias de un usuario — un usuario que nunca
 * visitó la pantalla de preferencias igual recibe defaults razonables desde
 * {@code NotificationDispatcher} (tratado como "todo habilitado excepto email" cuando no existe
 * fila todavía), así que esta fila es un override persistido, no una precondición para que las
 * notificaciones funcionen.
 */
@Entity
@Table(name = "notification_preferences")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "payment_reminders", nullable = false)
    private boolean paymentReminders;

    @Column(name = "overspend_alerts", nullable = false)
    private boolean overspendAlerts;

    @Column(name = "weekly_summary", nullable = false)
    private boolean weeklySummary;

    @Column(name = "inactivity_reminders", nullable = false)
    private boolean inactivityReminders;

    @Column(name = "card_cycle_close", nullable = false)
    private boolean cardCycleClose;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
