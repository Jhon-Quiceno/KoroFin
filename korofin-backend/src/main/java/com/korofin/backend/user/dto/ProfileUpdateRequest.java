package com.korofin.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de {@code PUT /api/users/profile}.
 *
 * @param name  nuevo nombre a mostrar
 * @param email nuevo correo electrónico; no puede coincidir con el de otro usuario
 *              (ver {@code UserService#updateProfile})
 */
public record ProfileUpdateRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String name,
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El correo electrónico no es válido")
        @Size(max = 180, message = "El correo electrónico no puede superar 180 caracteres")
        String email
) {
}
