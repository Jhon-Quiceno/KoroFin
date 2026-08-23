package com.korofin.backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de {@code PUT /api/users/password}. {@code currentPassword} se verifica contra el hash
 * guardado antes de aplicar el cambio (ver {@code UserService#changePassword}).
 *
 * @param currentPassword contraseña actual, en texto plano
 * @param newPassword     nueva contraseña a establecer, en texto plano
 */
public record PasswordChangeRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        String currentPassword,
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 6, max = 100, message = "La nueva contraseña debe tener entre 6 y 100 caracteres")
        String newPassword
) {
}
