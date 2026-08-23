package com.korofin.backend.mapper.user;

import com.korofin.backend.dto.user.UserResponse;
import com.korofin.backend.entity.user.User;
import org.mapstruct.Mapper;

/**
 * Mapea {@link User} a sus DTOs de salida. Nunca expone {@code passwordHash}.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
