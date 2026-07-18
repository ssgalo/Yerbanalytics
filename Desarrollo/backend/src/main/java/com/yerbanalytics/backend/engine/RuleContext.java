package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.dto.Metric;
import com.yerbanalytics.backend.dto.MetricSpec;
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
 *
 * <p>Campos que se irán incorporando en fases futuras (actualmente {@code null}):
 * <ul>
 *   <li>{@code forecast} — pronóstico climático (R2/HU-09).</li>
 *   <li>{@code diagnosis} — resultado del modelo IA (R3/HU-04).</li>
 *   <li>{@code bloqueoManualActivo} — bloqueo del operario (R3/HU-19).</li>
 *   <li>{@code sensorStale} — antigüedad de la telemetría (R1/HU-02).</li>
 * </ul>
 */
public record RuleContext(
        SectorEntity sector,
        ZonaEntity zona,
        List<MetricSpec> specs,
        List<Metric> metrics,
        ConfiguracionOperativaEntity config,
        String finalStatus,
        Instant now
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
