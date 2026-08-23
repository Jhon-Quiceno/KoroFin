package com.korofin.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Rate limiting básico en memoria para los endpoints más expuestos a abuso: login, registro, y
 * las dos rutas que disparan una llamada a un proveedor de IA pago/con límite propio por request
 * ({@code /api/ai/chat} y {@code /api/receipts/scan}).
 *
 * <p><b>Decisión de orden del filtro:</b> se registra vía
 * {@code addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)} en
 * {@link com.korofin.backend.config.SecurityConfig} — correr después de
 * {@link JwtAuthenticationFilter} significa que {@link SecurityContextHolder} ya tiene el usuario
 * autenticado (si lo hay) para cuando corre este filtro, así que las rutas de IA pueden usar clave
 * IP+userId en vez de solo IP: dos usuarios autenticados detrás del mismo NAT/oficina no comparten
 * el mismo balde.
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
    private static final String AI_CHAT_PATH = "/api/ai/chat";
    private static final String RECEIPT_SCAN_PATH = "/api/receipts/scan";

    private final InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter();

    private final int loginMaxRequests;
    private final Duration loginWindow;
    private final int registerMaxRequests;
    private final Duration registerWindow;
    private final int aiChatMaxRequests;
    private final Duration aiChatWindow;
    private final int receiptScanMaxRequests;
    private final Duration receiptScanWindow;

    public RateLimitFilter(
            @Value("${app.rate-limit.login.max-requests:5}") int loginMaxRequests,
            @Value("${app.rate-limit.login.window-seconds:60}") long loginWindowSeconds,
            @Value("${app.rate-limit.register.max-requests:3}") int registerMaxRequests,
            @Value("${app.rate-limit.register.window-seconds:300}") long registerWindowSeconds,
            @Value("${app.rate-limit.ai-chat.max-requests:10}") int aiChatMaxRequests,
            @Value("${app.rate-limit.ai-chat.window-seconds:60}") long aiChatWindowSeconds,
            @Value("${app.rate-limit.receipt-scan.max-requests:10}") int receiptScanMaxRequests,
            @Value("${app.rate-limit.receipt-scan.window-seconds:60}") long receiptScanWindowSeconds
    ) {
        this.loginMaxRequests = loginMaxRequests;
        this.loginWindow = Duration.ofSeconds(loginWindowSeconds);
        this.registerMaxRequests = registerMaxRequests;
        this.registerWindow = Duration.ofSeconds(registerWindowSeconds);
        this.aiChatMaxRequests = aiChatMaxRequests;
        this.aiChatWindow = Duration.ofSeconds(aiChatWindowSeconds);
        this.receiptScanMaxRequests = receiptScanMaxRequests;
        this.receiptScanWindow = Duration.ofSeconds(receiptScanWindowSeconds);
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
        if (AI_CHAT_PATH.equals(path)) {
            return new RateLimitRule(authenticatedKey(request, "ai-chat"), aiChatMaxRequests, aiChatWindow);
        }
        if (RECEIPT_SCAN_PATH.equals(path)) {
            return new RateLimitRule(authenticatedKey(request, "receipt-scan"), receiptScanMaxRequests, receiptScanWindow);
        }
        return null;
    }

    /**
     * Clave IP+userId (en vez de solo IP) para las dos rutas que disparan una llamada de IA por
     * request ({@code /api/ai/chat} y {@code /api/receipts/scan}), para que dos usuarios
     * autenticados detrás del mismo NAT/oficina no compartan un mismo balde.
     */
    private String authenticatedKey(HttpServletRequest request, String bucket) {
        String ip = clientIp(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            return ip + ":" + bucket + ":" + authentication.getPrincipal();
        }
        return ip + ":" + bucket;
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
