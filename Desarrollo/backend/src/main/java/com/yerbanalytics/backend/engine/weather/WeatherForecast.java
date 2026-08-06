package com.yerbanalytics.backend.engine.weather;

import java.time.Instant;

/**
 * Snapshot inmutable del pronóstico climático para la ubicación del vivero.
 *
 * <p>Campos que consumen las reglas del motor:
 * <ul>
 *   <li>{@link #probLluviaPct} — probabilidad de lluvia en el horizonte próximo (0–100).
 *       Evaluado por {@code ClimaOverrideRule} para posponer el riego.</li>
 *   <li>{@link #uvIndex} — índice UV (0–11+). Evaluado por {@code MediasombraRule}.</li>
 *   <li>{@link #timestamp} — momento en que se obtuvo el pronóstico; permite detectar
 *       si la cache está desactualizada.</li>
 * </ul>
 *
 * @param probLluviaPct probabilidad de lluvia en porcentaje (0.0 – 100.0)
 * @param uvIndex       índice UV (escala estándar 0–11+)
 * @param timestamp     instante de obtención del pronóstico
 */
public record WeatherForecast(
        double probLluviaPct,
        double uvIndex,
        Instant timestamp
) {
    /** @return true si el pronóstico fue emitido hace menos de {@code maxAgeMs} ms. */
    public boolean isFresh(long maxAgeMs) {
        return timestamp != null
                && (System.currentTimeMillis() - timestamp.toEpochMilli()) < maxAgeMs;
    }
}
