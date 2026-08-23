package com.korofin.backend.user.mapper;

import com.korofin.backend.user.dto.UserResponse;
import com.korofin.backend.user.entity.AppLanguage;
import com.korofin.backend.user.entity.ThemePreference;
import com.korofin.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void toResponseMapsAllExposedFields() {
        User user = new User();
        user.setId(1L);
        user.setName("Ana");
        user.setEmail("ana@mail.com");
        user.setPasswordHash("hash-nunca-expuesto");
        user.setTheme(ThemePreference.DARK);
        user.setCurrency("USD");
        user.setLanguage(AppLanguage.EN);

        UserResponse response = mapper.toResponse(user);

        assertThat(response).isEqualTo(new UserResponse(1L, "Ana", "ana@mail.com", ThemePreference.DARK, "USD", AppLanguage.EN));
    }
}
