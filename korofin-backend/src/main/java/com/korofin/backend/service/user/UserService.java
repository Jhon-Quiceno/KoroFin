package com.korofin.backend.service.user;

import com.korofin.backend.dto.user.AuthResponse;
import com.korofin.backend.dto.user.LoginRequest;
import com.korofin.backend.dto.user.PasswordChangeRequest;
import com.korofin.backend.dto.user.ProfileUpdateRequest;
import com.korofin.backend.dto.user.RegisterRequest;
import com.korofin.backend.dto.user.UserPreferencesResponse;
import com.korofin.backend.dto.user.UserPreferencesUpdateRequest;
import com.korofin.backend.dto.user.UserResponse;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.exception.ResourceNotFoundException;
import com.korofin.backend.exception.user.EmailAlreadyExistsException;
import com.korofin.backend.exception.user.InvalidCredentialsException;
import com.korofin.backend.exception.user.InvalidRefreshTokenException;
import com.korofin.backend.mapper.user.UserMapper;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/**
 * Orquesta registro, login, refresh/logout de sesión, perfil, contraseña y preferencias del
 * usuario.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userMapper = userMapper;
    }

    @Transactional
    public AuthSession register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyExistsException("Ya existe un usuario registrado con este correo electrónico");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);

        User createdUser = userRepository.save(user);
        // Un registro nuevo arranca sin "recordar sesión" (rememberMe = false): el default más
        // seguro cuando el usuario todavía no optó explícitamente por mantenerla.
        return buildAuthSession(createdUser, false);
    }

    /**
     * Autentica al usuario.
     *
     * <p>TODO(ai): cuando exista el dominio {@code ai} (fase posterior), este método debe borrar
     * el historial de chat de IA de tipo {@code CHAT} del usuario (no las filas de tipo
     * {@code INSIGHT}, que sobreviven al login) para que cada sesión nueva arranque el asistente
     * con la conversación en blanco — comportamiento heredado de FinSmart
     * ({@code UserService#login}), documentado acá para que la fase de IA no lo pase por alto.
     */
    @Transactional
    public AuthSession login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        User user = userRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Correo o contraseña inválidos"));

        if (!user.isActive()) {
            throw new InvalidCredentialsException("Correo o contraseña inválidos");
        }

        boolean isPasswordValid = passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!isPasswordValid) {
            throw new InvalidCredentialsException("Correo o contraseña inválidos");
        }

        user.setLastLoginAt(Instant.now());
        boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
        return buildAuthSession(user, rememberMe);
    }

    @Transactional
    public AuthSession refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token requerido");
        }

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(refreshToken);
        return buildAuthSession(result.user(), result.refreshToken(), result.rememberMe());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /**
     * Actualiza el nombre y correo del usuario actual, rechazando el cambio si el nuevo correo ya
     * está tomado por otro usuario.
     *
     * @throws ResourceNotFoundException   si no existe un usuario con {@code userId}
     * @throws EmailAlreadyExistsException si otro usuario ya tiene el correo de {@code request}
     */
    @Transactional
    public UserResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        String normalizedEmail = normalizeEmail(request.email());
        boolean emailChanged = !normalizedEmail.equalsIgnoreCase(user.getEmail());
        if (emailChanged && userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyExistsException("Ya existe un usuario registrado con este correo electrónico");
        }

        user.setName(request.name().trim());
        user.setEmail(normalizedEmail);

        try {
            User updatedUser = userRepository.save(user);
            return userMapper.toResponse(updatedUser);
        } catch (DataIntegrityViolationException ex) {
            // Ventana angosta de TOCTOU: otro request tomó el mismo correo entre el chequeo de
            // arriba y este save. El constraint único de la BD es el respaldo real; se mapea al
            // mismo 409 que hubiera producido el chequeo previo, en vez de dejarlo salir como 500.
            throw new EmailAlreadyExistsException("Ya existe un usuario registrado con este correo electrónico");
        }
    }

    /**
     * Cambia la contraseña del usuario actual tras verificar {@code request.currentPassword()}
     * contra el hash guardado, y revoca todos los demás refresh tokens activos de este usuario
     * para que una sesión robada/vigente no pueda seguir usando las credenciales viejas.
     *
     * @throws ResourceNotFoundException   si no existe un usuario con {@code userId}
     * @throws InvalidCredentialsException si {@code request.currentPassword()} no coincide con el
     *                                      hash guardado
     */
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("La contraseña actual no es correcta");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);
    }

    /**
     * @throws ResourceNotFoundException si no existe un usuario con {@code userId}
     */
    @Transactional(readOnly = true)
    public UserPreferencesResponse getPreferences(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return new UserPreferencesResponse(user.getTheme(), user.getCurrency(), user.getLanguage());
    }

    /**
     * @throws ResourceNotFoundException si no existe un usuario con {@code userId}
     */
    @Transactional
    public UserPreferencesResponse updatePreferences(Long userId, UserPreferencesUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user.setTheme(request.theme());
        user.setCurrency(request.currency());
        user.setLanguage(request.language());
        User updatedUser = userRepository.save(user);

        return new UserPreferencesResponse(updatedUser.getTheme(), updatedUser.getCurrency(), updatedUser.getLanguage());
    }

    private AuthSession buildAuthSession(User user, boolean rememberMe) {
        String refreshToken = refreshTokenService.createForUser(user, rememberMe);
        return buildAuthSession(user, refreshToken, rememberMe);
    }

    /**
     * KoroFin es mobile-only puro: a diferencia de FinSmart, {@code refreshToken} siempre viaja en
     * el body de {@link AuthResponse} — no hay rama condicional por cliente ni cookie que armar.
     */
    private AuthSession buildAuthSession(User user, String refreshToken, boolean rememberMe) {
        String accessToken = jwtService.generateAccessToken(user);
        UserResponse userResponse = userMapper.toResponse(user);
        AuthResponse response = new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds(),
                userResponse,
                refreshToken
        );

        return new AuthSession(response, refreshToken, rememberMe);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
