package com.korofin.backend.user.mapper;

import com.korofin.backend.user.dto.UserResponse;
import com.korofin.backend.user.entity.User;
import org.mapstruct.Mapper;

/**
 * Mapea {@link User} a sus DTOs de salida. Nunca expone {@code passwordHash}.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
