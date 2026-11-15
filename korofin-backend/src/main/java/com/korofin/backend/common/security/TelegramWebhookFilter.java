package com.korofin.backend.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Autenticación server-to-server para las tres rutas de webhook de n8n del dominio
 * {@code integration} (ver docs/backend-plan.md sección 5): secreto compartido en el header
 * {@code X-Telegram-Webhook-Secret}, comparación en tiempo constante
 * ({@link MessageDigest#isEqual}) para no filtrar el secreto por temporización.
 *
 * <p><b>Apagado por defecto:</b> si {@code app.telegram.webhook-secret} está vacío/no configurado,
 * las tres rutas devuelven {@code 401} siempre, sin importar el header recibido — la integración
 * de Telegram es opcional y arranca desactivada hasta que se configure explícitamente
 * {@code TELEGRAM_WEBHOOK_SECRET}.
 *
 * <p>Mismo criterio de dependencias que {@link RateLimitFilter}: solo primitivos {@code @Value}
 * en el constructor, para que los slices {@code @WebMvcTest} existentes (que auto-detectan todo
 * {@code @Component} que implementa {@code Filter}) no necesiten proveer un bean adicional.
 */
@Component
public class TelegramWebhookFilter extends OncePerRequestFilter {

    private static final String SECRET_HEADER = "X-Telegram-Webhook-Secret";

    private static final String TELEGRAM_BASE_PATH = "/api/integrations/telegram";

    /** Única ruta del prefijo que usa el JWT del usuario (la llama la app), no el secreto de n8n. */
    private static final String LINK_CODE_PATH = TELEGRAM_BASE_PATH + "/link-code";

    private final String webhookSecret;

    public TelegramWebhookFilter(@Value("${app.telegram.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!requiresSecret(RequestPaths.canonical(request))) {
            filterChain.doFilter(request, response);
            return;
        }

        if (webhookSecret.isEmpty() || !isValidSecret(request.getHeader(SECRET_HEADER))) {
            writeUnauthorized(response, request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Falla cerrado: <b>toda</b> ruta bajo {@code /api/integrations/telegram} exige el secreto salvo
     * {@code link-code}. Antes se comparaba la ruta cruda contra tres literales y cualquier otra
     * grafía de la misma ruta (por ejemplo con un carácter en porcentaje) llegaba al controlador
     * sin secreto, y una ruta nueva del controlador quedaba sin protección hasta que alguien
     * recordara agregarla a la lista.
     */
    private static boolean requiresSecret(String canonicalPath) {
        boolean underTelegram = canonicalPath.equals(TELEGRAM_BASE_PATH)
                || canonicalPath.startsWith(TELEGRAM_BASE_PATH + "/");
        return underTelegram && !canonicalPath.equals(LINK_CODE_PATH);
    }

    private boolean isValidSecret(String providedSecret) {
        if (providedSecret == null) {
            return false;
        }
        byte[] expected = webhookSecret.getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        String body = """
                {"timestamp":"%s","status":401,"error":"Unauthorized","message":"Secreto de webhook inválido o no configurado.","path":"%s"}"""
                .formatted(Instant.now(), escapeJson(path));
        response.getWriter().write(body);
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
