package com.yerbanalytics.backend.engine.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio de pronóstico climático con cache thread-safe y retry automático.
 *
 * <p>Orquesta la obtención del pronóstico via el {@link WeatherClient} configurado,
 * aplicando:
 * <ul>
 *   <li><b>Cache:</b> {@link AtomicReference} — thread-safe sin bloqueos. Con el trigger
 *       reactivo (MQTT) y el Watchdog ({@code @Scheduled}) corriendo en paralelo, una
 *       cache mutable sin sincronizar sería una condición de carrera.</li>
 *   <li><b>Retry:</b> hasta {@code maxRetries} intentos con espera exponencial (
 *       {@code 1s → 2s → 4s}) antes de declarar degradación.</li>
 *   <li><b>TTL:</b> si el pronóstico en cache es fresco (< {@code cacheTtlMs}), se
 *       retorna directamente sin llamar a la API.</li>
 *   <li><b>Fallback:</b> si todos los reintentos fallan, retorna {@code null}. El motor
 *       opera en modo degradado (sin clima) sin detener la evaluación.</li>
 * </ul>
 *
 * <h3>Configuración</h3>
 * <ul>
 *   <li>{@code yerbanalytics.weather.cache-ttl-ms} — TTL de la cache (default: 15 min)</li>
 *   <li>{@code yerbanalytics.weather.max-retries} — reintentos antes de degradar (default: 3)</li>
 *   <li>{@code yerbanalytics.weather.retry-base-ms} — espera base del backoff (default: 1000 ms)</li>
 * </ul>
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final WeatherClient client;
    private final long cacheTtlMs;
    private final int maxRetries;
    private final long retryBaseMs;

    /** Cache thread-safe del último pronóstico válido. */
    private final AtomicReference<WeatherForecast> cache = new AtomicReference<>(null);

    public WeatherService(
            WeatherClient client,
            @Value("${yerbanalytics.weather.cache-ttl-ms:900000}") long cacheTtlMs,
            @Value("${yerbanalytics.weather.max-retries:3}") int maxRetries,
            @Value("${yerbanalytics.weather.retry-base-ms:1000}") long retryBaseMs) {
        this.client = client;
        this.cacheTtlMs = cacheTtlMs;
        this.maxRetries = maxRetries;
        this.retryBaseMs = retryBaseMs;
    }

    /**
     * Retorna el pronóstico climático vigente, usando la cache si está fresca o
     * llamando a la API con retry si venció.
     *
     * @return un {@link WeatherForecast} válido, o {@code null} si la API no pudo
     *         responder (el motor opera en modo degradado).
     */
    public WeatherForecast getForecast() {
        WeatherForecast cached = cache.get();
        if (cached != null && cached.isFresh(cacheTtlMs)) {
            log.debug("WeatherService: retornando pronóstico desde cache.");
            return cached;
        }

        WeatherForecast fresh = fetchWithRetry();
        if (fresh != null) {
            cache.set(fresh);
            log.info("WeatherService: pronóstico actualizado — lluvia {}%, UV {}.",
                    fresh.probLluviaPct(), fresh.uvIndex());
        } else {
            log.warn("WeatherService: no se pudo obtener pronóstico tras {} reintentos. " +
                     "El motor opera en modo degradado (sin clima).", maxRetries);
        }
        return fresh;
    }

    private WeatherForecast fetchWithRetry() {
        long waitMs = retryBaseMs;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            WeatherForecast result = client.fetch();
            if (result != null) {
                return result;
            }
            if (attempt < maxRetries) {
                log.debug("WeatherService: intento {}/{} fallido, esperando {} ms.", attempt, maxRetries, waitMs);
                sleep(waitMs);
                waitMs *= 2; // exponential backoff
            }
        }
        return null;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Invalida la cache manualmente (útil en tests). */
    void invalidateCache() {
        cache.set(null);
    }
}
