package com.korofin.backend.notification.mapper;

import com.korofin.backend.notification.dto.NotificationResponse;
import com.korofin.backend.notification.entity.Notification;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct entre {@link Notification} y su DTO de respuesta.
 *
 * <p>Las notificaciones nunca se crean ni actualizan desde un DTO enviado por el cliente (siempre
 * las arma {@code NotificationService#createNotification} del lado del servidor), así que este
 * mapper solo necesita la dirección de lectura.
 */
@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}
