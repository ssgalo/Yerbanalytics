package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.dto.Metric;
import com.yerbanalytics.backend.dto.MetricSpec;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot inmutable de todo lo que las reglas del motor necesitan para decidir.
 *
 * <p>Se construye <b>una sola vez</b> por ciclo de evaluación (en {@code NurseryService})
 * y se comparte entre todas las reglas. Ninguna regla puede modificarlo; solo emite
 * {@link RuleAction acciones}.
 */
public record RuleContext(
        SectorEntity sector,
        ZonaEntity zona,
        List<MetricSpec> specs,
        List<Metric> metrics,
        ConfiguracionOperativaEntity config,
        String finalStatus,
        Instant now,

        // --- Campos incorporados en fases R1/R2/R3 ---

        /**
         * {@code true} si el nodo testigo de la macro-zona no reportó telemetría
         * dentro del umbral configurado ({@code yerbanalytics.nursery.stale-threshold-ms}).
         * Evaluado por {@code StaleSensorRule}.
         */
        boolean sensorStale,

        /**
         * Pronóstico climático obtenido por {@code WeatherService} (R2/HU-09).
         * Puede ser {@code null} si la API externa no respondió — el motor opera
         * en modo degradado sin cancelar la evaluación.
         */
        WeatherForecast forecast,

        /**
         * {@code true} si existe un {@code BloqueoManualEntity} activo para este sector
         * o su zona (R3/HU-19). Evaluado por {@code BloqueoManualRule}.
         */
        boolean bloqueoManualActivo

) {
    /**
     * Devuelve el valor raw de la métrica identificada por {@code key},
     * o {@code null} si no está presente en el contexto.
     */
    public Double metricRaw(String key) {
        return metrics.stream()
                .filter(m -> key.equals(m.key()))
                .map(Metric::raw)
                .findFirst()
                .orElse(null);
    }
}
