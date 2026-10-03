package com.yerbanalytics.backend.engine.weather;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
 * @param horas          marcas horarias desde la hora actual hasta +24 h (hora local del vivero), con
 *                       probabilidad y milímetros; vacía si el pronóstico no las trae
 */
public record WeatherForecast(
        double probLluviaPct,
        double uvIndex,
        Instant timestamp,
        double tempC,
        String cond,
        double humRel,
        List<ForecastSlotRaw> forecastSlots,
        List<PronosticoHora> horas
) {
    /** Constructor sin horas: mantiene la firma de antes de los milímetros (widget y mediasombra). */
    public WeatherForecast(double probLluviaPct, double uvIndex, Instant timestamp, double tempC, String cond,
                           double humRel, List<ForecastSlotRaw> forecastSlots) {
        this(probLluviaPct, uvIndex, timestamp, tempC, cond, humRel, forecastSlots, List.of());
    }

    /**
     * Lluvia prevista en las próximas {@code horas} horas desde {@code ahora} (hora local del vivero).
     *
     * <p>Open-Meteo marca cada valor horario con el FIN de su hora ({@code 15:00} = 14:00–15:00),
     * así que "las próximas N horas" desde las 10:20 son las marcas T con
     * {@code trunc(ahora) < T ≤ trunc(ahora) + N}: 11:00, 12:00, 13:00 y 14:00 para N = 4. La marca de
     * las 10:00 ya pasó y la de las 15:00 queda afuera.
     *
     * @return probabilidad horaria máxima, milímetros acumulados (suma, sin redondeo binario) y cuántas
     *         marcas había disponibles: menos que {@code horas} si el pronóstico no alcanza
     */
    public LluviaPrevista lluviaProxima(LocalDateTime ahora, int horas) {
        LocalDateTime desde = ahora.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime hasta = desde.plusHours(horas);
        double probMax = 0.0;
        BigDecimal mm = BigDecimal.ZERO;
        int cubiertas = 0;
        for (PronosticoHora h : this.horas) {
            if (h.hora().isAfter(desde) && !h.hora().isAfter(hasta)) {
                probMax = Math.max(probMax, h.probLluviaPct());
                mm = mm.add(BigDecimal.valueOf(h.precipitacionMm()));
                cubiertas++;
            }
        }
        return new LluviaPrevista(probMax, mm.doubleValue(), cubiertas);
    }

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

    /**
     * Una marca horaria del pronóstico.
     *
     * @param hora           marca en hora local del vivero (fin de la hora que describe)
     * @param probLluviaPct  probabilidad de precipitación en % (0–100)
     * @param precipitacionMm precipitación prevista en milímetros
     */
    public record PronosticoHora(LocalDateTime hora, double probLluviaPct, double precipitacionMm) {}

    /**
     * Lluvia prevista en una ventana de horas.
     *
     * @param probMaxPct    probabilidad horaria máxima en la ventana
     * @param mmTotal       milímetros acumulados en la ventana
     * @param horasCubiertas cantidad de marcas horarias disponibles dentro de la ventana
     */
    public record LluviaPrevista(double probMaxPct, double mmTotal, int horasCubiertas) {}
}
