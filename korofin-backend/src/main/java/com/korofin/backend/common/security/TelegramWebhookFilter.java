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
import java.util.Set;

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

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/integrations/telegram/confirm-link",
            "/api/integrations/telegram/expenses",
            "/api/integrations/telegram/receipts"
    );

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
        String path = request.getRequestURI();
        if (!PROTECTED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (webhookSecret.isEmpty() || !isValidSecret(request.getHeader(SECRET_HEADER))) {
            writeUnauthorized(response, path);
            return;
        }

        filterChain.doFilter(request, response);
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
