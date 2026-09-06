package com.korofin.backend.notification.mapper;

import com.korofin.backend.notification.dto.NotificationResponse;
import com.korofin.backend.notification.entity.Notification;
import com.korofin.backend.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMapperTest {

    private final NotificationMapper mapper = Mappers.getMapper(NotificationMapper.class);

    @Test
    void toResponseMapsEveryField() {
        Instant createdAt = Instant.parse("2026-01-01T08:00:00Z");
        Instant readAt = Instant.parse("2026-01-02T08:00:00Z");

        Notification notification = new Notification();
        notification.setId(1L);
        notification.setType(NotificationType.PAYMENT_REMINDER);
        notification.setTitle("Recordatorio");
        notification.setMessage("Tu servicio vence pronto");
        notification.setRead(true);
        notification.setReadAt(readAt);
        notification.setCreatedAt(createdAt);

        NotificationResponse response = mapper.toResponse(notification);

        assertThat(response).isEqualTo(new NotificationResponse(
                1L, NotificationType.PAYMENT_REMINDER, "Recordatorio", "Tu servicio vence pronto",
                true, readAt, createdAt
        ));
    }

    @Test
    void toResponseLeavesReadAtNullWhenUnread() {
        Notification notification = new Notification();
        notification.setId(2L);
        notification.setType(NotificationType.SYSTEM);
        notification.setTitle("Aviso");
        notification.setMessage("Cuerpo");
        notification.setRead(false);

        NotificationResponse response = mapper.toResponse(notification);

        assertThat(response.read()).isFalse();
        assertThat(response.readAt()).isNull();
    }
}
