package com.korofin.backend.user.controller;

import com.korofin.backend.user.dto.AuthResponse;
import com.korofin.backend.user.dto.LoginRequest;
import com.korofin.backend.user.dto.PasswordChangeRequest;
import com.korofin.backend.user.dto.ProfileUpdateRequest;
import com.korofin.backend.user.dto.RefreshRequest;
import com.korofin.backend.user.dto.RegisterRequest;
import com.korofin.backend.user.dto.UserPreferencesResponse;
import com.korofin.backend.user.dto.UserPreferencesUpdateRequest;
import com.korofin.backend.user.dto.UserResponse;
import com.korofin.backend.common.exception.ErrorResponse;
import com.korofin.backend.common.security.SecurityUtils;
import com.korofin.backend.user.service.AuthSession;
import com.korofin.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticación, perfil y preferencias. KoroFin es mobile-only puro (ver
 * plan-backend.md sección 3.2): un solo camino, sin header {@code X-Client}, sin cookie, sin CSRF
 * — a diferencia de FinSmart, que sostenía dos ramas (web con cookie + mobile con body).
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Autenticacion", description = "Endpoints para registro, login y sesion de usuarios")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Registrar nuevo usuario", description = "Crea una cuenta nueva y devuelve access + refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario registrado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese correo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthSession session = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session.response());
    }

    @Operation(summary = "Iniciar sesion", description = "Autentica un usuario y devuelve access + refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion iniciada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales invalidas",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthSession session = userService.login(request);
        return ResponseEntity.ok(session.response());
    }

    @Operation(summary = "Refrescar sesion", description = "Rota el refresh token recibido en el body y devuelve un access + refresh token nuevos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion refrescada exitosamente"),
            @ApiResponse(responseCode = "401", description = "Refresh token invalido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthSession session = userService.refresh(request.refreshToken());
        return ResponseEntity.ok(session.response());
    }

    @Operation(summary = "Cerrar sesion", description = "Revoca el refresh token recibido en el body")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesion cerrada exitosamente")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        userService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Actualizar perfil", description = "Actualiza el nombre y correo del usuario autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese correo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @Operation(summary = "Cambiar contraseña", description = "Cambia la contraseña del usuario autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contraseña cambiada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "La contraseña actual no es correcta",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Obtener preferencias", description = "Devuelve el tema, la moneda y el idioma del usuario autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferencias disponibles")
    })
    @GetMapping("/preferences")
    public ResponseEntity<UserPreferencesResponse> getPreferences() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(userService.getPreferences(userId));
    }

    @Operation(summary = "Actualizar preferencias", description = "Actualiza el tema, la moneda y el idioma del usuario autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferencias actualizadas exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/preferences")
    public ResponseEntity<UserPreferencesResponse> updatePreferences(@Valid @RequestBody UserPreferencesUpdateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(userService.updatePreferences(userId, request));
    }
}
