package com.korofin.backend.notification.repository;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.notification.entity.NotificationPreference;
import com.korofin.backend.notification.entity.PushToken;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cubre {@link NotificationPreferenceRepository} y {@link PushTokenRepository} juntos: ambos son
 * repositorios pequeños, de solo un puñado de queries, sobre entidades directamente relacionadas
 * (preferencias y tokens de notificación del mismo usuario) — mismo criterio que
 * {@code DebtLedgerRepositoryTest} en el dominio {@code debt}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NotificationPreferenceAndPushTokenRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUser_IdReturnsEmptyWhenNoPreferenceRowExists() {
        User owner = userRepository.saveAndFlush(newUser("no-pref@korofin.dev"));

        assertThat(notificationPreferenceRepository.findByUser_Id(owner.getId())).isEmpty();
    }

    @Test
    void onlyOnePreferenceRowIsAllowedPerUser() {
        User owner = userRepository.saveAndFlush(newUser("dup-pref@korofin.dev"));
        notificationPreferenceRepository.saveAndFlush(newPreference(owner));

        assertThatThrownBy(() -> notificationPreferenceRepository.saveAndFlush(newPreference(owner)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByUser_IdAndDeviceIdFindsTheRegisteredToken() {
        User owner = userRepository.saveAndFlush(newUser("push-owner@korofin.dev"));
        pushTokenRepository.saveAndFlush(newPushToken(owner, "device-1", "ExponentPushToken[abc]"));

        assertThat(pushTokenRepository.findByUser_IdAndDeviceId(owner.getId(), "device-1")).isPresent();
        assertThat(pushTokenRepository.findByUser_IdAndDeviceId(owner.getId(), "device-2")).isEmpty();
    }

    @Test
    void sameUserAndDeviceCannotHaveTwoTokenRows() {
        User owner = userRepository.saveAndFlush(newUser("push-dup@korofin.dev"));
        pushTokenRepository.saveAndFlush(newPushToken(owner, "device-dup", "ExponentPushToken[abc]"));

        assertThatThrownBy(() -> pushTokenRepository.saveAndFlush(newPushToken(owner, "device-dup", "ExponentPushToken[xyz]")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteByUser_IdAndDeviceIdRemovesOnlyTheOwnersToken() {
        User owner = userRepository.saveAndFlush(newUser("push-del-owner@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("push-del-other@korofin.dev"));
        pushTokenRepository.saveAndFlush(newPushToken(owner, "device-x", "token-owner"));

        int deletedByOther = pushTokenRepository.deleteByUser_IdAndDeviceId(other.getId(), "device-x");
        assertThat(deletedByOther).isZero();
        assertThat(pushTokenRepository.findByUser_IdAndDeviceId(owner.getId(), "device-x")).isPresent();

        int deletedByOwner = pushTokenRepository.deleteByUser_IdAndDeviceId(owner.getId(), "device-x");
        assertThat(deletedByOwner).isEqualTo(1);
        assertThat(pushTokenRepository.findByUser_IdAndDeviceId(owner.getId(), "device-x")).isEmpty();
    }

    private NotificationPreference newPreference(User owner) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUser(owner);
        preference.setPaymentReminders(true);
        preference.setOverspendAlerts(true);
        preference.setWeeklySummary(true);
        preference.setInactivityReminders(true);
        preference.setCardCycleClose(true);
        preference.setEmailEnabled(false);
        return preference;
    }

    private PushToken newPushToken(User owner, String deviceId, String expoPushToken) {
        PushToken pushToken = new PushToken();
        pushToken.setUser(owner);
        pushToken.setDeviceId(deviceId);
        pushToken.setExpoPushToken(expoPushToken);
        return pushToken;
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
