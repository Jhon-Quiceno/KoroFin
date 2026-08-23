package com.korofin.backend.notification.service.channel;

import com.korofin.backend.notification.entity.NotificationPreference;
import com.korofin.backend.notification.entity.NotificationType;
import com.korofin.backend.notification.entity.PushToken;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.notification.repository.NotificationPreferenceRepository;
import com.korofin.backend.notification.repository.PushTokenRepository;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationSender notificationSender;

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private PushNotificationSender pushNotificationSender;

    private NotificationDispatcher dispatcher;

    private void buildDispatcher() {
        dispatcher = new NotificationDispatcher(
                notificationPreferenceRepository, notificationService, userRepository,
                notificationSender, pushTokenRepository, pushNotificationSender
        );
    }

    @Test
    void dispatchSkipsEverythingWhenTypeIsDisabledByPreference() {
        buildDispatcher();
        NotificationPreference preference = new NotificationPreference();
        preference.setPaymentReminders(false);
        when(notificationPreferenceRepository.findByUser_Id(1L)).thenReturn(Optional.of(preference));

        dispatcher.dispatch(1L, NotificationType.PAYMENT_REMINDER, "t", "m", "key");

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
        verify(notificationSender, never()).send(any(), any(), any());
        verify(pushTokenRepository, never()).findByUser_Id(any());
    }

    @Test
    void dispatchTreatsMissingPreferenceRowAsEverythingEnabledExceptEmail() {
        buildDispatcher();
        when(notificationPreferenceRepository.findByUser_Id(1L)).thenReturn(Optional.empty());
        when(pushTokenRepository.findByUser_Id(1L)).thenReturn(List.of());

        dispatcher.dispatch(1L, NotificationType.PAYMENT_REMINDER, "t", "m", "key");

        verify(notificationService).createNotification(1L, NotificationType.PAYMENT_REMINDER, "t", "m", "key");
        verify(notificationSender, never()).send(any(), any(), any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void dispatchSendsEmailOnlyWhenPreferenceEnablesIt() {
        buildDispatcher();
        NotificationPreference preference = new NotificationPreference();
        preference.setPaymentReminders(true);
        preference.setEmailEnabled(true);
        when(notificationPreferenceRepository.findByUser_Id(1L)).thenReturn(Optional.of(preference));
        User user = new User();
        user.setId(1L);
        user.setEmail("user@korofin.dev");
        user.setName("Ana");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pushTokenRepository.findByUser_Id(1L)).thenReturn(List.of());

        dispatcher.dispatch(1L, NotificationType.PAYMENT_REMINDER, "Título", "Mensaje", "key");

        ArgumentCaptor<EmailRecipient> captor = ArgumentCaptor.forClass(EmailRecipient.class);
        verify(notificationSender).send(captor.capture(), eq("Título"), eq("Mensaje"));
        assertThat(captor.getValue().email()).isEqualTo("user@korofin.dev");
    }

    @Test
    void dispatchSendsOnePushPerRegisteredToken() {
        buildDispatcher();
        when(notificationPreferenceRepository.findByUser_Id(1L)).thenReturn(Optional.empty());
        PushToken tokenA = new PushToken();
        tokenA.setExpoPushToken("token-a");
        PushToken tokenB = new PushToken();
        tokenB.setExpoPushToken("token-b");
        when(pushTokenRepository.findByUser_Id(1L)).thenReturn(List.of(tokenA, tokenB));

        dispatcher.dispatch(1L, NotificationType.SYSTEM, "t", "m", null);

        verify(pushNotificationSender, times(2)).send(any(PushRecipient.class), eq("t"), eq("m"));
    }

    @Test
    void dispatchAlwaysDeliversMonthEndPredictionAndSystemRegardlessOfMissingToggle() {
        buildDispatcher();
        NotificationPreference preference = new NotificationPreference();
        when(notificationPreferenceRepository.findByUser_Id(1L)).thenReturn(Optional.of(preference));
        when(pushTokenRepository.findByUser_Id(1L)).thenReturn(List.of());

        dispatcher.dispatch(1L, NotificationType.SYSTEM, "t", "m", null);

        verify(notificationService).createNotification(1L, NotificationType.SYSTEM, "t", "m", null);
    }
}
