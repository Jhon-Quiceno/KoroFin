package com.korofin.backend.notification.mapper;

import com.korofin.backend.notification.dto.NotificationPreferenceRequest;
import com.korofin.backend.notification.dto.NotificationPreferenceResponse;
import com.korofin.backend.notification.entity.NotificationPreference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper MapStruct entre {@link NotificationPreference} y sus DTOs de request/response.
 *
 * <p>{@link #updateEntityFromRequest} ignora {@code id}, {@code user}, {@code createdAt} y
 * {@code updatedAt} — esos los administra {@code NotificationService} (asignación del dueño y
 * auditoría), nunca el payload del cliente.
 */
@Mapper(componentModel = "spring")
public interface NotificationPreferenceMapper {

    NotificationPreferenceResponse toResponse(NotificationPreference preference);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(
            NotificationPreferenceRequest request, @MappingTarget NotificationPreference preference
    );
}
