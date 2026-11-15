package com.korofin.backend.integration.service.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Almacén en memoria de códigos de vínculo de un solo uso, con TTL corto — mismo criterio de
 * arquitectura que {@code InMemoryRateLimiter} (dominio {@code security}, fase 1): en memoria, no
 * distribuido, adecuado para un despliegue de una sola instancia (ver docs/backend-plan.md
 * sección 5).
 *
 * <p>{@link #generate} lo llama {@code TelegramLinkService} desde una request autenticada de la
 * app (JWT); {@link #consume} lo llama al confirmar el vínculo desde n8n (sin JWT,
 * {@code TelegramWebhookFilter}). El código se consume exactamente una vez: {@link #consume}
 * remueve la entrada del mapa tanto si es válida como si ya expiró, así que un código usado o
 * vencido nunca puede reintentarse.
 */
@Component
public class TelegramLinkCodeStore {

    private static final int CODE_LENGTH = 6;
    private static final String DIGITS = "0123456789";

    /** Sorteos de un código antes de rendirse cuando todos chocan con códigos vigentes. */
    private static final int MAX_DRAW_ATTEMPTS = 20;

    private final Clock clock;
    private final Duration ttl;
    private final Supplier<String> codeSource;
    private final ConcurrentHashMap<String, CodeEntry> codes = new ConcurrentHashMap<>();

    @Autowired
    public TelegramLinkCodeStore(
            Clock clock,
            @Value("${app.telegram.link-code.ttl-seconds:300}") long ttlSeconds
    ) {
        this(clock, ttlSeconds, secureRandomCodeSource());
    }

    /** Permite guionar los sorteos en los tests para forzar colisiones. */
    TelegramLinkCodeStore(Clock clock, long ttlSeconds, Supplier<String> codeSource) {
        this.clock = clock;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.codeSource = codeSource;
    }

    /**
     * Genera un código numérico de {@value #CODE_LENGTH} dígitos para {@code userId}, válido por
     * {@code app.telegram.link-code.ttl-seconds}.
     *
     * <p>El vínculo código→usuario es estable durante toda la vida del código: nunca reemplaza una
     * entrada vigente de otro usuario (si el sorteo choca, se vuelve a sortear) y descarta los
     * códigos vencidos y el código anterior del mismo usuario, de modo que cada usuario tiene a lo
     * sumo un código vivo. Si todos los sorteos chocan, lanza {@link IllegalStateException} en vez
     * de pisar el código de otro.
     */
    public String generate(Long userId) {
        Instant now = clock.instant();
        codes.values().removeIf(entry -> now.isAfter(entry.expiresAt()) || entry.userId().equals(userId));
        for (int attempt = 0; attempt < MAX_DRAW_ATTEMPTS; attempt++) {
            String code = codeSource.get();
            if (codes.putIfAbsent(code, new CodeEntry(userId, now.plus(ttl))) == null) {
                return code;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de vínculo único");
    }

    /**
     * @return el {@code userId} asociado a {@code code}, o {@link Optional#empty()} si el código
     *         nunca existió, ya se consumió, o expiró. Siempre remueve la entrada del mapa cuando
     *         existe, sin importar si todavía era válida — de un solo uso por diseño.
     */
    public Optional<Long> consume(String code) {
        CodeEntry entry = codes.remove(code);
        if (entry == null || clock.instant().isAfter(entry.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    /** @return el TTL configurado, en segundos — usado por {@code TelegramLinkService} para la respuesta. */
    public long ttlSeconds() {
        return ttl.getSeconds();
    }

    private static Supplier<String> secureRandomCodeSource() {
        SecureRandom random = new SecureRandom();
        return () -> {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
            }
            return code.toString();
        };
    }

    private record CodeEntry(Long userId, Instant expiresAt) {
    }
}
