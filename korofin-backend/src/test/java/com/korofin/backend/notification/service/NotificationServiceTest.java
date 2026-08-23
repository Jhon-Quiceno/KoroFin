package com.korofin.backend.notification.service;

import com.korofin.backend.notification.dto.NotificationPreferenceRequest;
import com.korofin.backend.notification.dto.NotificationPreferenceResponse;
import com.korofin.backend.notification.dto.NotificationResponse;
import com.korofin.backend.notification.dto.PushTokenRequest;
import com.korofin.backend.notification.entity.Notification;
import com.korofin.backend.notification.entity.NotificationPreference;
import com.korofin.backend.notification.entity.NotificationType;
import com.korofin.backend.notification.entity.PushToken;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.common.exception.ResourceNotFoundException;
import com.korofin.backend.notification.mapper.NotificationMapper;
import com.korofin.backend.notification.mapper.NotificationPreferenceMapper;
import com.korofin.backend.notification.repository.NotificationPreferenceRepository;
import com.korofin.backend.notification.repository.NotificationRepository;
import com.korofin.backend.notification.repository.PushTokenRepository;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationPreferenceMapper notificationPreferenceMapper;

    @Mock
    private PushTokenRepository pushTokenRepository;

    @InjectMocks
    private NotificationService notificationService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void markAsReadSetsReadFlagAndTimestampWhenCurrentlyUnread() {
        setAuthenticatedUser(1L);
        Notification notification = new Notification();
        notification.setId(5L);
        notification.setRead(false);
        when(notificationRepository.findByIdAndUser_Id(5L, 1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);
        when(notificationMapper.toResponse(notification)).thenReturn(mockResponse(5L, true));

        NotificationResponse response = notificationService.markAsRead(5L);

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
        assertThat(response.read()).isTrue();
    }

    @Test
    void markAsReadDoesNotResaveWhenAlreadyRead() {
        setAuthenticatedUser(1L);
        Notification notification = new Notification();
        notification.setId(5L);
        notification.setRead(true);
        notification.setReadAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(notificationRepository.findByIdAndUser_Id(5L, 1L)).thenReturn(Optional.of(notification));
        when(notificationMapper.toResponse(notification)).thenReturn(mockResponse(5L, true));

        notificationService.markAsRead(5L);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsReadThrowsNotFoundWhenNotificationBelongsToAnotherUser() {
        setAuthenticatedUser(1L);
        when(notificationRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getNotificationsScopesToCurrentUser() {
        setAuthenticatedUser(4L);
        Pageable pageable = PageRequest.of(0, 20);
        Notification notification = new Notification();
        Page<Notification> page = new PageImpl<>(List.of(notification), pageable, 1);
        when(notificationRepository.findByUser_IdOrderByCreatedAtDesc(4L, pageable)).thenReturn(page);
        when(notificationMapper.toResponse(notification)).thenReturn(mockResponse(1L, false));

        Page<NotificationResponse> result = notificationService.getNotifications(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(notificationRepository).findByUser_IdOrderByCreatedAtDesc(4L, pageable);
    }

    @Test
    void createNotificationSkipsWhenDedupeKeyAlreadyExists() {
        when(notificationRepository.existsByUser_IdAndDedupeKey(1L, "key")).thenReturn(true);

        notificationService.createNotification(1L, NotificationType.SYSTEM, "t", "m", "key");

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createNotificationSwallowsRaceConditionOnDuplicateInsert() {
        when(notificationRepository.existsByUser_IdAndDedupeKey(1L, "key")).thenReturn(false);
        when(userRepository.getReferenceById(1L)).thenReturn(new User());
        when(notificationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        notificationService.createNotification(1L, NotificationType.SYSTEM, "t", "m", "key");

        verify(notificationRepository).saveAndFlush(any());
    }

    @Test
    void createNotificationWithNullDedupeKeySkipsTheExistenceCheck() {
        when(userRepository.getReferenceById(1L)).thenReturn(new User());

        notificationService.createNotification(1L, NotificationType.SYSTEM, "t", "m", null);

        verify(notificationRepository, never()).existsByUser_IdAndDedupeKey(any(), any());
        verify(notificationRepository).saveAndFlush(any());
    }

    @Test
    void getPreferencesCreatesDefaultsWhenNoRowExistsYet() {
        setAuthenticatedUser(2L);
        when(notificationPreferenceRepository.findByUser_Id(2L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(2L)).thenReturn(new User());
        when(notificationPreferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationPreferenceMapper.toResponse(any(NotificationPreference.class)))
                .thenReturn(new NotificationPreferenceResponse(true, true, true, true, true, false));

        NotificationPreferenceResponse response = notificationService.getPreferences();

        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(notificationPreferenceRepository).save(captor.capture());
        assertThat(captor.getValue().isPaymentReminders()).isTrue();
        assertThat(captor.getValue().isEmailEnabled()).isFalse();
        assertThat(response.emailEnabled()).isFalse();
    }

    @Test
    void updatePreferencesReusesExistingRowInsteadOfCreatingANewOne() {
        setAuthenticatedUser(2L);
        NotificationPreference existing = new NotificationPreference();
        when(notificationPreferenceRepository.findByUser_Id(2L)).thenReturn(Optional.of(existing));
        when(notificationPreferenceRepository.save(existing)).thenReturn(existing);
        when(notificationPreferenceMapper.toResponse(existing))
                .thenReturn(new NotificationPreferenceResponse(true, true, true, true, true, true));

        NotificationPreferenceRequest request = new NotificationPreferenceRequest(true, true, true, true, true, true);
        notificationService.updatePreferences(request);

        verify(notificationPreferenceMapper).updateEntityFromRequest(request, existing);
        verify(userRepository, never()).getReferenceById(any());
    }

    @Test
    void registerPushTokenUpsertsExistingTokenForTheSameDevice() {
        setAuthenticatedUser(3L);
        PushToken existing = new PushToken();
        existing.setId(9L);
        when(pushTokenRepository.findByUser_IdAndDeviceId(3L, "device-1")).thenReturn(Optional.of(existing));

        notificationService.registerPushToken(new PushTokenRequest("new-token", "device-1"));

        ArgumentCaptor<PushToken> captor = ArgumentCaptor.forClass(PushToken.class);
        verify(pushTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9L);
        assertThat(captor.getValue().getExpoPushToken()).isEqualTo("new-token");
        verify(userRepository, never()).getReferenceById(any());
    }

    @Test
    void registerPushTokenCreatesNewRowForANewDevice() {
        setAuthenticatedUser(3L);
        when(pushTokenRepository.findByUser_IdAndDeviceId(3L, "device-2")).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(3L)).thenReturn(new User());

        notificationService.registerPushToken(new PushTokenRequest("token", "device-2"));

        verify(pushTokenRepository).save(any(PushToken.class));
    }

    @Test
    void unregisterPushTokenDelegatesToScopedDelete() {
        setAuthenticatedUser(3L);

        notificationService.unregisterPushToken("device-1");

        verify(pushTokenRepository, times(1)).deleteByUser_IdAndDeviceId(3L, "device-1");
    }

    @Test
    void markAllAsReadDelegatesToBulkRepositoryUpdate() {
        setAuthenticatedUser(1L);

        notificationService.markAllAsRead();

        verify(notificationRepository).markAllAsReadForUser(1L);
    }

    private NotificationResponse mockResponse(Long id, boolean read) {
        return new NotificationResponse(id, NotificationType.SYSTEM, "t", "m", read, null, null);
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }
}
