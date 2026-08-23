package com.korofin.backend.controller.notification;

import com.korofin.backend.dto.notification.NotificationPreferenceRequest;
import com.korofin.backend.dto.notification.NotificationPreferenceResponse;
import com.korofin.backend.dto.notification.NotificationResponse;
import com.korofin.backend.dto.notification.PushTokenRequest;
import com.korofin.backend.service.notification.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de las notificaciones y preferencias del usuario actual.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(notificationService.getNotifications(pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        return ResponseEntity.ok(notificationService.getUnreadCount());
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable("id") Long notificationId) {
        return ResponseEntity.ok(notificationService.markAsRead(notificationId));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> getPreferences() {
        return ResponseEntity.ok(notificationService.getPreferences());
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> updatePreferences(
            @Valid @RequestBody NotificationPreferenceRequest request
    ) {
        return ResponseEntity.ok(notificationService.updatePreferences(request));
    }

    @PostMapping("/push-token")
    public ResponseEntity<Void> registerPushToken(@Valid @RequestBody PushTokenRequest request) {
        notificationService.registerPushToken(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/push-token/{deviceId}")
    public ResponseEntity<Void> unregisterPushToken(@PathVariable String deviceId) {
        notificationService.unregisterPushToken(deviceId);
        return ResponseEntity.noContent().build();
    }
}
