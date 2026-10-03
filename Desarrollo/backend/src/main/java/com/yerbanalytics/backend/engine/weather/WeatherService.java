package com.yerbanalytics.backend.engine.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio de pronóstico climático con cache thread-safe, retry automático y fallo cacheado.
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
 *   <li><b>Fallo cacheado:</b> si todos los reintentos fallan, el fallo también se recuerda
 *       {@code failureCacheTtlMs} (60 s por defecto): sin eso cada consulta (por mensaje de telemetría, por
 *       sector, por request del dashboard) repetiría los reintentos con espera mientras no haya internet.</li>
 *   <li><b>Fallback:</b> sin pronóstico retorna {@code null}. El motor opera en modo degradado (sin clima)
 *       sin detener la evaluación.</li>
 * </ul>
 *
 * <p><b>Dos formas de pedirlo.</b> {@link #getForecast()} puede bloquear (HTTP + reintentos con espera): para el
 * barrido del watchdog y el snapshot. {@link #getForecastSinEspera()} <b>nunca</b> bloquea ni duerme: es para el hilo de
 * la telemetría, que evalúa 100 sectores por mensaje y sostiene a R-02. Devuelve lo que haya cacheado (aunque haya
 * vencido, hasta {@value #FACTOR_USABLE_VENCIDO} TTL) y refresca en un hilo aparte, una consulta a la vez.
 *
 * <h3>Configuración</h3>
 * <ul>
 *   <li>{@code yerbanalytics.weather.cache-ttl-ms} — TTL de la cache (default: 15 min)</li>
 *   <li>{@code yerbanalytics.weather.failure-cache-ttl-ms} — cuánto se recuerda un fallo (default: 60 s)</li>
 *   <li>{@code yerbanalytics.weather.max-retries} — reintentos antes de degradar (default: 3)</li>
 *   <li>{@code yerbanalytics.weather.retry-base-ms} — espera base del backoff (default: 1000 ms)</li>
 * </ul>
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    /** Un pronóstico vencido se sigue usando (mientras se refresca aparte) hasta esta cantidad de TTL. */
    static final int FACTOR_USABLE_VENCIDO = 4;
    private static final long SIN_FALLO = Long.MIN_VALUE;

    private final WeatherClient client;
    private final long cacheTtlMs;
    private final int maxRetries;
    private final long retryBaseMs;
    private final long failureCacheTtlMs;
    private final Clock reloj;
    /** Donde corren los refrescos que no pueden esperar en el hilo llamador. */
    private final Executor refrescador;

    /** Cache thread-safe del último pronóstico válido. */
    private final AtomicReference<WeatherForecast> cache = new AtomicReference<>(null);
    /** Cuándo (epoch ms) falló por última vez una consulta completa; {@link #SIN_FALLO} si no hay fallo vigente. */
    private final AtomicLong ultimoFalloMs = new AtomicLong(SIN_FALLO);
    private final AtomicBoolean refrescando = new AtomicBoolean(false);

    @Autowired
    public WeatherService(
            WeatherClient client,
            @Value("${yerbanalytics.weather.cache-ttl-ms:900000}") long cacheTtlMs,
            @Value("${yerbanalytics.weather.max-retries:3}") int maxRetries,
            @Value("${yerbanalytics.weather.retry-base-ms:1000}") long retryBaseMs,
            @Value("${yerbanalytics.weather.failure-cache-ttl-ms:60000}") long failureCacheTtlMs,
            Clock reloj) {
        this(client, cacheTtlMs, maxRetries, retryBaseMs, failureCacheTtlMs, reloj, hiloDeRefresco());
    }

    WeatherService(WeatherClient client, long cacheTtlMs, int maxRetries, long retryBaseMs, long failureCacheTtlMs,
                   Clock reloj, Executor refrescador) {
        this.client = client;
        this.cacheTtlMs = cacheTtlMs;
        this.maxRetries = maxRetries;
        this.retryBaseMs = retryBaseMs;
        this.failureCacheTtlMs = failureCacheTtlMs;
        this.reloj = reloj;
        this.refrescador = refrescador;
    }

    private static Executor hiloDeRefresco() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "weather-refresh");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Retorna el pronóstico climático vigente, usando la cache si está fresca o
     * llamando a la API con retry si venció. <b>Puede bloquear</b> (HTTP y esperas del backoff): no usar desde el
     * hilo de la telemetría, ver {@link #getForecastSinEspera()}.
     *
     * @return un {@link WeatherForecast} válido, o {@code null} si la API no pudo
     *         responder (el motor opera en modo degradado) o falló hace menos de {@code failureCacheTtlMs}.
     */
    public WeatherForecast getForecast() {
        WeatherForecast cached = cache.get();
        if (cached != null && cached.isFresh(cacheTtlMs, reloj.instant())) {
            log.debug("WeatherService: retornando pronóstico desde cache.");
            return cached;
        }
        if (falloReciente()) {
            log.debug("WeatherService: la API falló hace menos de {} ms; no se reintenta.", failureCacheTtlMs);
            return null;
        }
        return consultar();
    }

    /**
     * Pronóstico para el hilo de la telemetría: <b>nunca espera ni duerme</b>. Devuelve el cacheado si es fresco;
     * si venció pero tiene menos de {@value #FACTOR_USABLE_VENCIDO} TTL lo sigue devolviendo (mejor un pronóstico
     * de hace 20 min que ninguno); y en cualquier caso, si no hay un fallo reciente, lanza el refresco en un hilo
     * aparte (a lo sumo uno a la vez). Sin nada usable devuelve {@code null}: la evaluación sigue sin pronóstico y
     * la traza muestra la comparación sin dato (O-01).
     */
    public WeatherForecast getForecastSinEspera() {
        WeatherForecast cached = cache.get();
        Instant ahora = reloj.instant();
        if (cached != null && cached.isFresh(cacheTtlMs, ahora)) {
            return cached;
        }
        if (!falloReciente()) {
            refrescarAparte();
        }
        return cached != null && cached.isFresh(FACTOR_USABLE_VENCIDO * cacheTtlMs, ahora) ? cached : null;
    }

    /**
     * Pide el pronóstico en un hilo aparte apenas arranca la aplicación. Sin esto el primer mensaje del nodo no
     * encuentra pronóstico cacheado ({@link #getForecastSinEspera()} devuelve {@code null} en frío), R-03 queda sin dato
     * y R-01 puede encolar una ronda que la lluvia habría pospuesto. No bloquea el arranque ni lo rompe si falla.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void precalentar() {
        refrescarAparte();
    }

    private void refrescarAparte() {
        if (!refrescando.compareAndSet(false, true)) {
            return;   // ya hay una consulta en vuelo
        }
        try {
            refrescador.execute(() -> {
                try {
                    consultar();
                } catch (RuntimeException e) {
                    ultimoFalloMs.set(reloj.millis());
                    log.warn("WeatherService: falló el refresco del pronóstico ({}).", e.toString());
                } finally {
                    refrescando.set(false);
                }
            });
        } catch (RuntimeException e) {
            refrescando.set(false);
            log.warn("WeatherService: no se pudo lanzar el refresco del pronóstico ({}).", e.toString());
        }
    }

    /** Consulta la API con reintentos y deja el resultado (pronóstico o fallo) cacheado. */
    private WeatherForecast consultar() {
        WeatherForecast fresh = fetchWithRetry();
        if (fresh != null) {
            cache.set(fresh);
            ultimoFalloMs.set(SIN_FALLO);
            log.info("WeatherService: pronóstico actualizado — lluvia {}%, UV {}.",
                    fresh.probLluviaPct(), fresh.uvIndex());
        } else {
            ultimoFalloMs.set(reloj.millis());
            log.warn("WeatherService: no se pudo obtener pronóstico tras {} reintentos. " +
                     "El motor opera en modo degradado (sin clima) y no reintenta por {} ms.",
                     maxRetries, failureCacheTtlMs);
        }
        return fresh;
    }

    private boolean falloReciente() {
        long fallo = ultimoFalloMs.get();
        return fallo != SIN_FALLO && reloj.millis() - fallo < failureCacheTtlMs;
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
        ultimoFalloMs.set(SIN_FALLO);
    }
}
