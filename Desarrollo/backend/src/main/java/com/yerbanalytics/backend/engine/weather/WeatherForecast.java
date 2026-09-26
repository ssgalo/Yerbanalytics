package com.yerbanalytics.backend.engine.weather;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot inmutable del pronóstico climático para la ubicación del vivero.
 *
 * <p>Campos que consumen las reglas del motor:
 * <ul>
 *   <li>{@link #probLluviaPct} — probabilidad de lluvia en el horizonte próximo (0–100).
 *       Evaluado por {@code WeatherOverrideRule} para posponer el riego.</li>
 *   <li>{@link #uvIndex} — índice UV (0–11+). Evaluado por {@code ShadingRule}.</li>
 *   <li>{@link #timestamp} — momento en que se obtuvo el pronóstico; permite detectar
 *       si la cache está desactualizada.</li>
 * </ul>
 *
 * <p>Campos adicionales para el widget de clima del dashboard:
 * <ul>
 *   <li>{@link #tempC} — temperatura del aire en °C (hora actual).</li>
 *   <li>{@link #cond} — condición legible: "Despejado", "Parcial nublado", "Lluvia", etc.</li>
 *   <li>{@link #humRel} — humedad relativa del aire en % (hora actual).</li>
 *   <li>{@link #forecastSlots} — próximas 4 horas con UV y probabilidad de lluvia.</li>
 * </ul>
 *
 * @param probLluviaPct  probabilidad de lluvia en porcentaje (0.0 – 100.0)
 * @param uvIndex        índice UV (escala estándar 0–11+)
 * @param timestamp      instante de obtención del pronóstico
 * @param tempC          temperatura del aire en °C
 * @param cond           condición climática legible
 * @param humRel         humedad relativa en %
 * @param forecastSlots  slots horarios futuros para el widget de previsión
 */
public record WeatherForecast(
        double probLluviaPct,
        double uvIndex,
        Instant timestamp,
        double tempC,
        String cond,
        double humRel,
        List<ForecastSlotRaw> forecastSlots
) {
    /** @return true si el pronóstico fue emitido hace menos de {@code maxAgeMs} ms. */
    public boolean isFresh(long maxAgeMs) {
        return timestamp != null
                && (System.currentTimeMillis() - timestamp.toEpochMilli()) < maxAgeMs;
    }

    /**
     * Slot horario de previsión (UV + probabilidad de lluvia) para el widget del dashboard.
     *
     * @param label etiqueta legible de la franja, ej. "15 h" o "Mañana"
     * @param uv    índice UV previsto
     * @param rain  probabilidad de lluvia en %
     */
    public record ForecastSlotRaw(String label, double uv, double rain) {}
}
