package com.korofin.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Ruta canónica de un request, solo para <b>decidir</b> qué regla de seguridad aplica
 * ({@link RateLimitFilter}, {@link TelegramWebhookFilter}).
 *
 * <p>Por qué existe: {@code request.getRequestURI()} devuelve la ruta tal como llegó, sin decodificar,
 * mientras que el enrutamiento de Spring MVC y el matcher de Spring Security trabajan con la ruta
 * ya decodificada. Una grafía distinta de la misma ruta (por ejemplo {@code /api/users/%6Cogin}, que
 * decodifica a {@code /api/users/login}) llegaba al mismo handler pero no coincidía con la comparación
 * exacta de los filtros, y se saltaba el límite de intentos o el secreto del webhook.
 *
 * <p>La canonicalización es deliberadamente <b>más amplia</b> que la de Spring: decodifica
 * porcentajes, descarta parámetros de ruta ({@code ;x=1}), segmentos vacíos, {@code .} y {@code ..},
 * quita la barra final y pasa a minúsculas. Que un filtro aplique una regla a una grafía que Spring
 * no enrutaría es inocuo; lo peligroso es lo contrario.
 */
final class RequestPaths {

    private RequestPaths() {
    }

    static String canonical(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "/";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }

        String decoded = decode(stripPathParameters(uri));

        Deque<String> segments = new ArrayDeque<>();
        for (String segment : decoded.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                segments.pollLast();
                continue;
            }
            segments.addLast(segment.toLowerCase(Locale.ROOT));
        }
        return "/" + String.join("/", segments);
    }

    /** Quita {@code ;parametros} de cada segmento, antes de decodificar (un {@code %3B} no es un separador). */
    private static String stripPathParameters(String uri) {
        StringBuilder result = new StringBuilder(uri.length());
        for (String segment : uri.split("/", -1)) {
            int semicolon = segment.indexOf(';');
            result.append(semicolon >= 0 ? segment.substring(0, semicolon) : segment).append('/');
        }
        result.setLength(result.length() - 1);
        return result.toString();
    }

    private static String decode(String value) {
        try {
            return UriUtils.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidEncoding) {
            // Un porcentaje mal formado no se puede decodificar: se usa tal cual, que sigue sirviendo
            // para las comparaciones por prefijo.
            return value;
        }
    }
}
