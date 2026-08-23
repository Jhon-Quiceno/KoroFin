package com.korofin.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Rate limiting básico en memoria para los endpoints más expuestos a abuso de esta fase: login y
 * registro. Las reglas de {@code ai-chat}/{@code receipt-scan} (que necesitan combinar IP+userId,
 * ver el criterio de orden de filtros abajo) las agrega la fase de IA cuando ese dominio exista.
 *
 * <p><b>Decisión de orden del filtro:</b> se registra vía
 * {@code addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)} en {@link
 * com.korofin.backend.config.SecurityConfig} — aunque las reglas de esta fase (login/register) son
 * IP-only y no necesitan el contexto de seguridad ya poblado, correr después de
 * {@link JwtAuthenticationFilter} deja el filtro listo para que las reglas futuras de IA combinen
 * IP+userId sin tener que reordenar la cadena de filtros más adelante.
 *
 * <p><b>Las dependencias del constructor están limitadas a primitivos inyectados con
 * {@code @Value}</b> (sin bean de {@link InMemoryRateLimiter}): este filtro es un
 * {@code @Component} que implementa {@link jakarta.servlet.Filter}, así que los slices
 * {@code @WebMvcTest} de Spring Boot lo auto-detectan e instancian en cada test de controller del
 * módulo, use o no ese test {@code @Import(SecurityConfig.class)} explícitamente. Depender de
 * cualquier bean adicional gestionado por Spring acá obligaría a todos los {@code @WebMvcTest}
 * existentes a proveer uno.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/users/login";
    private static final String REGISTER_PATH = "/api/users/register";

    private final InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter();

    private final int loginMaxRequests;
    private final Duration loginWindow;
    private final int registerMaxRequests;
    private final Duration registerWindow;

    public RateLimitFilter(
            @Value("${app.rate-limit.login.max-requests:5}") int loginMaxRequests,
            @Value("${app.rate-limit.login.window-seconds:60}") long loginWindowSeconds,
            @Value("${app.rate-limit.register.max-requests:3}") int registerMaxRequests,
            @Value("${app.rate-limit.register.window-seconds:300}") long registerWindowSeconds
    ) {
        this.loginMaxRequests = loginMaxRequests;
        this.loginWindow = Duration.ofSeconds(loginWindowSeconds);
        this.registerMaxRequests = registerMaxRequests;
        this.registerWindow = Duration.ofSeconds(registerWindowSeconds);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        RateLimitRule rule = resolveRule(path, request);

        if (rule == null || rateLimiter.tryConsume(rule.key(), rule.maxRequests(), rule.window())) {
            filterChain.doFilter(request, response);
            return;
        }

        writeTooManyRequests(response, path);
    }

    private RateLimitRule resolveRule(String path, HttpServletRequest request) {
        if (LOGIN_PATH.equals(path)) {
            return new RateLimitRule(clientIp(request) + ":login", loginMaxRequests, loginWindow);
        }
        if (REGISTER_PATH.equals(path)) {
            return new RateLimitRule(clientIp(request) + ":register", registerMaxRequests, registerWindow);
        }
        return null;
    }

    /**
     * Usa siempre la IP del socket ({@code request.getRemoteAddr()}), nunca el header
     * {@code X-Forwarded-For}: ese header lo controla el cliente que hace el request, y sin una
     * lista de proxies de confianza configurada (no existe hoy en este servicio) confiar en su
     * primer valor permite a un atacante rotar IPs falsas para esquivar el rate limit por
     * completo. Si en el futuro el backend queda detrás de un proxy/load balancer real, esta
     * lógica debe actualizarse junto con esa configuración, no antes.
     */
    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private static void writeTooManyRequests(HttpServletResponse response, String path) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        String body = """
                {"timestamp":"%s","status":429,"error":"Too Many Requests","message":"Demasiadas solicitudes, intentá de nuevo en un momento.","path":"%s"}"""
                .formatted(Instant.now(), escapeJson(path));
        response.getWriter().write(body);
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record RateLimitRule(String key, int maxRequests, Duration window) {
    }
}
