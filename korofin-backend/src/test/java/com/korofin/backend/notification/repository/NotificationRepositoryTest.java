package com.korofin.backend.notification.repository;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.notification.entity.Notification;
import com.korofin.backend.notification.entity.NotificationType;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NotificationRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByIdAndUser_IdReturnsEmptyForAnotherUsersNotification() {
        User owner = userRepository.saveAndFlush(newUser("owner-notif@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("other-notif@korofin.dev"));
        Notification notification = notificationRepository.saveAndFlush(newNotification(owner, null));

        assertThat(notificationRepository.findByIdAndUser_Id(notification.getId(), owner.getId())).isPresent();
        assertThat(notificationRepository.findByIdAndUser_Id(notification.getId(), other.getId())).isEmpty();
    }

    @Test
    void countByUser_IdAndReadFalseCountsOnlyUnread() {
        User owner = userRepository.saveAndFlush(newUser("unread@korofin.dev"));
        Notification unread = newNotification(owner, null);
        Notification read = newNotification(owner, null);
        read.setRead(true);
        notificationRepository.saveAndFlush(unread);
        notificationRepository.saveAndFlush(read);

        assertThat(notificationRepository.countByUser_IdAndReadFalse(owner.getId())).isEqualTo(1);
    }

    @Test
    void markAllAsReadForUserUpdatesEveryUnreadRowInOneStatement() {
        User owner = userRepository.saveAndFlush(newUser("mark-all@korofin.dev"));
        notificationRepository.saveAndFlush(newNotification(owner, null));
        notificationRepository.saveAndFlush(newNotification(owner, null));

        int updated = notificationRepository.markAllAsReadForUser(owner.getId());

        assertThat(updated).isEqualTo(2);
        assertThat(notificationRepository.countByUser_IdAndReadFalse(owner.getId())).isZero();
    }

    @Test
    void existsByUser_IdAndDedupeKeyDetectsExistingDedupeKey() {
        User owner = userRepository.saveAndFlush(newUser("dedupe-exists@korofin.dev"));
        notificationRepository.saveAndFlush(newNotification(owner, "payment-reminder:1:2026-01-01"));

        assertThat(notificationRepository.existsByUser_IdAndDedupeKey(owner.getId(), "payment-reminder:1:2026-01-01")).isTrue();
        assertThat(notificationRepository.existsByUser_IdAndDedupeKey(owner.getId(), "otra-clave")).isFalse();
    }

    /**
     * El índice único parcial {@code uk_notifications_user_dedupe_key} rechaza un segundo insert
     * con el mismo {@code (user, dedupeKey)} — la defensa definitiva contra duplicados que respalda
     * el chequeo previo en memoria de {@code NotificationService#createNotification}.
     */
    @Test
    void savingTwoNotificationsWithTheSameDedupeKeyForTheSameUserViolatesTheUniqueConstraint() {
        User owner = userRepository.saveAndFlush(newUser("dedupe-unique@korofin.dev"));
        notificationRepository.saveAndFlush(newNotification(owner, "dup-key"));

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(newNotification(owner, "dup-key")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nullDedupeKeyNeverCollidesEvenWithMultipleRows() {
        User owner = userRepository.saveAndFlush(newUser("dedupe-null@korofin.dev"));
        notificationRepository.saveAndFlush(newNotification(owner, null));
        notificationRepository.saveAndFlush(newNotification(owner, null));

        assertThat(notificationRepository.findByUser_IdOrderByCreatedAtDesc(owner.getId(), PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(2);
    }

    private Notification newNotification(User owner, String dedupeKey) {
        Notification notification = new Notification();
        notification.setUser(owner);
        notification.setType(NotificationType.SYSTEM);
        notification.setTitle("Título");
        notification.setMessage("Mensaje");
        notification.setRead(false);
        notification.setDedupeKey(dedupeKey);
        return notification;
    }

    private User newUser(String email) {
        User user = new User();
        user.setName("Test");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setActive(true);
        return user;
    }
}
