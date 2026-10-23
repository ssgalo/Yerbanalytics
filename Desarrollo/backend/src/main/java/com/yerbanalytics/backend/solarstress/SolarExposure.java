package com.yerbanalytics.backend.solarstress;

import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.VentanaHoraria;
import com.yerbanalytics.backend.readings.ReadingHistory;
import com.yerbanalytics.backend.readings.ReadingMetric;
import com.yerbanalytics.backend.readings.ReadingWindow;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Cuánto sol tuvo la macro-zona en las últimas 48 h, en los términos de F-S1 y F-S3 (reglas_v2 §8.5): el pico de luz entre
 * las 10 y las 16 h y el pico de temperatura. Es la única pregunta de las dos reglas al historial de lecturas, así las dos
 * miran lo mismo y cada una sólo compara.
 *
 * <p>La luz es el <b>% de un LDR</b>, no radiación (CLAUDE.md §2). Una métrica sin lecturas en la ventana no tiene pico
 * ({@code null}): ninguna regla decide con un dato que no hay.
 */
@Service
public class SolarExposure {

    /** Las últimas 48 h que piden la v2 para las dos reglas. */
    static final Duration WINDOW = Duration.ofHours(48);

    /** De 10 a 16 h, cuando el sol es fuerte: una lectura de luz de la mañana temprano o de la tarde no cuenta. */
    static final VentanaHoraria MIDDAY = VentanaHoraria.parse("10:00-16:00");

    /**
     * @param light el pico de luz (%) entre las 10 y las 16 h, o {@code null} si no hay lecturas
     * @param temp  el pico de temperatura del aire (°C), o {@code null} si no hay lecturas
     */
    public record Peaks(Double light, Double temp) {
    }

    private final ReadingHistory readings;

    public SolarExposure(ReadingHistory readings) {
        this.readings = readings;
    }

    /**
     * @param lastReadingOnly {@code true} = el bypass {@code samples} del simulador: el pico es la última lectura de la zona
     *                        (descartada por S-03 cuenta como sin dato) y no se consulta el historial
     */
    public Peaks peaks(RuleContext ctx, boolean lastReadingOnly) {
        if (lastReadingOnly) {
            return new Peaks(ctx.validMetricRaw(ReadingMetric.UV.key()), ctx.validMetricRaw(ReadingMetric.TEMP.key()));
        }
        String zoneId = ctx.zona().getId();
        ReadingWindow last48h = ReadingWindow.since(ctx.now().minus(WINDOW));
        return new Peaks(
                readings.max(zoneId, ReadingMetric.UV, last48h.withBand(MIDDAY)).orElse(null),
                readings.max(zoneId, ReadingMetric.TEMP, last48h).orElse(null));
    }
}
