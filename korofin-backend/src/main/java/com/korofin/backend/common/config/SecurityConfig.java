package com.korofin.backend.common.config;

import com.korofin.backend.common.security.JwtAuthenticationFilter;
import com.korofin.backend.common.security.RateLimitFilter;
import com.korofin.backend.common.security.TelegramWebhookFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cadena de seguridad mobile-only, desde el día uno (ver plan-backend.md sección 3.2). A
 * diferencia de FinSmart (que sostenía un cliente web híbrido), acá no hay:
 *
 * <ul>
 *   <li><b>CSRF:</b> protege contra un navegador enviando credenciales ambientales (cookies) a un
 *       sitio que no las pidió. Un cliente Bearer-only no tiene credencial ambiente que un
 *       atacante pueda hacer viajar sin su cooperación — no hay superficie que proteger.</li>
 *   <li><b>CORS de aplicación:</b> un cliente HTTP nativo (Dio/http de Flutter) no corre en un
 *       origen de navegador — CORS no lo restringe ni lo protege.</li>
 *   <li><b>Cookies:</b> el refresh token siempre viaja en el body JSON, nunca en una cookie
 *       {@code HttpOnly}.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final TelegramWebhookFilter telegramWebhookFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter,
            TelegramWebhookFilter telegramWebhookFilter
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.telegramWebhookFilter = telegramWebhookFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/users/register",
                    "/api/users/login",
                    "/api/users/refresh",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/health"
                ).permitAll()
                // Sin JWT a propósito: n8n es servidor-a-servidor y no tiene sesión de usuario.
                // Su propia seguridad la aplica TelegramWebhookFilter (secreto compartido), no
                // Spring Security — ver docs/backend-plan.md sección 5.
                .requestMatchers(
                    "/api/integrations/telegram/confirm-link",
                    "/api/integrations/telegram/expenses",
                    "/api/integrations/telegram/receipts"
                ).permitAll()
                .anyRequest().authenticated()
            )
            // Corre ANTES que JwtAuthenticationFilter: las rutas de webhook de Telegram nunca
            // llevan un Authorization Bearer, así que su rechazo/aceptación no depende de él.
            .addFilterBefore(telegramWebhookFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // Corre DESPUÉS de JwtAuthenticationFilter: las reglas de esta fase (login/register)
            // son IP-only y no lo necesitan, pero deja la cadena lista para que las reglas de
            // ai-chat/receipt-scan (fase de IA) combinen IP+userId sin reordenar filtros.
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
