package com.korofin.backend.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acceso estático al id del usuario autenticado actual, usado por todos los servicios de dominio.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AccessDeniedException("Usuario no autenticado");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number number) {
            return number.longValue();
        }

        if (principal instanceof String principalAsString) {
            try {
                return Long.valueOf(principalAsString);
            } catch (NumberFormatException ex) {
                throw new AccessDeniedException("Usuario autenticado inválido");
            }
        }

        throw new AccessDeniedException("Usuario autenticado inválido");
    }
}
