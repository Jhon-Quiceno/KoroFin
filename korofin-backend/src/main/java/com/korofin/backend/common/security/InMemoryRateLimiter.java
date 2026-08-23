package com.korofin.backend.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter simple en memoria, por clave, con ventana fija. Decisión de arquitectura
 * documentada en el plan del backend (sección 6): en memoria, bucket por IP+usuario, no
 * distribuido — un despliegue de una sola instancia no necesita coordinación entre nodos todavía.
 *
 * <p>No es un bean de Spring a propósito: {@link RateLimitFilter} construye su propia instancia
 * directamente para que el constructor del filtro solo dependa de propiedades primitivas
 * {@code @Value}, manteniéndolo trivialmente construible en slices {@code @WebMvcTest} que no
 * configuran un contexto completo.
 *
 * <p>La ventana de cada clave se reinicia perezosamente en la siguiente llamada a
 * {@link #tryConsume} una vez que expiró, en vez de con un barrido en segundo plano — así el mapa
 * queda acotado por la cantidad de claves activas distintas (IPs/usuarios), sin necesitar una
 * tarea de limpieza programada.
 */
public class InMemoryRateLimiter {

    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    /** Constructor visible al paquete, usado por los tests para controlar el tiempo de forma determinista. */
    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * @return {@code true} si el request para {@code key} está permitido (y ya quedó contado
     *         contra su ventana), {@code false} si {@code key} ya alcanzó {@code maxRequests}
     *         dentro de la {@code window} actual
     */
    public boolean tryConsume(String key, int maxRequests, Duration window) {
        Instant now = clock.instant();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(now));

        synchronized (bucket) {
            if (Duration.between(bucket.windowStart, now).compareTo(window) >= 0) {
                bucket.windowStart = now;
                bucket.count.set(0);
            }

            if (bucket.count.get() >= maxRequests) {
                return false;
            }

            bucket.count.incrementAndGet();
            return true;
        }
    }

    private static final class Bucket {
        private volatile Instant windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        private Bucket(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
