package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.dto.Metric;
import com.yerbanalytics.backend.dto.MetricSpec;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
         * Pronóstico climático obtenido por {@code WeatherService} (R2/HU-09).
         * Puede ser {@code null} si la API externa no respondió — el motor opera
         * en modo degradado sin cancelar la evaluación.
         */
        WeatherForecast forecast,

        /**
         * {@code true} si existe un {@code ManualLockEntity} activo para este sector
         * o su zona (R2/HU-19). Evaluado por {@code ManualLockRule}.
         */
        boolean bloqueoManualActivo,

        /**
         * Pasado reciente del sector que necesitan las reglas de riego (último riego, última
         * aplicación de insumo, inicio del ciclo de lectura…). {@link ContextoRiego#vacio()} si
         * el llamador no lo arma.
         */
        ContextoRiego riego,

        /**
         * Overrides en vigor (cooldowns y guardas). {@link RuleOverrides#NONE} en producción: cada regla usa su
         * configuración. Nunca {@code null}.
         */
        RuleOverrides overrides,

        /**
         * Métricas descartadas en este ciclo por lectura inválida (S-03): agotaron sus relecturas fuera del rango
         * físico. Las reglas que las usan no actúan (las leen como "sin dato"); las demás métricas siguen vigentes.
         * Nunca {@code null}; vacío en la gran mayoría de las evaluaciones.
         */
        Set<String> invalidMetrics,

        /**
         * Qué hardware tiene mapeado el sector y su macro-zona (S-04), armado UNA vez por zona para que las reglas no
         * consulten la base. {@link HardwareCoverage#COMPLETE} si el llamador no lo arma. Nunca {@code null}.
         */
        HardwareCoverage hardware,

        /**
         * Qué actuadores del sector y su macro-zona están marcados como averiados (S-05), armado UNA vez por zona.
         * {@link HardwareFaults#NONE} si el llamador no lo arma. Nunca {@code null}.
         */
        HardwareFaults faults

) {
    public RuleContext {
        overrides = overrides == null ? RuleOverrides.NONE : overrides;
        invalidMetrics = invalidMetrics == null ? Set.of() : Set.copyOf(invalidMetrics);
        hardware = hardware == null ? HardwareCoverage.COMPLETE : hardware;
        faults = faults == null ? HardwareFaults.NONE : faults;
    }

    /** Constructor sin fallas: mantiene la firma de antes de S-05. */
    public RuleContext(SectorEntity sector, ZonaEntity zona, List<MetricSpec> specs, List<Metric> metrics,
                       ConfiguracionOperativaEntity config, String finalStatus, Instant now,
                       WeatherForecast forecast, boolean bloqueoManualActivo, ContextoRiego riego,
                       RuleOverrides overrides, Set<String> invalidMetrics, HardwareCoverage hardware) {
        this(sector, zona, specs, metrics, config, finalStatus, now, forecast, bloqueoManualActivo, riego,
                overrides, invalidMetrics, hardware, HardwareFaults.NONE);
    }

    /** Constructor sin hardware: mantiene la firma de antes de S-04. */
    public RuleContext(SectorEntity sector, ZonaEntity zona, List<MetricSpec> specs, List<Metric> metrics,
                       ConfiguracionOperativaEntity config, String finalStatus, Instant now,
                       WeatherForecast forecast, boolean bloqueoManualActivo, ContextoRiego riego,
                       RuleOverrides overrides, Set<String> invalidMetrics) {
        this(sector, zona, specs, metrics, config, finalStatus, now, forecast, bloqueoManualActivo, riego,
                overrides, invalidMetrics, HardwareCoverage.COMPLETE, HardwareFaults.NONE);
    }

    /** Constructor sin métricas descartadas: mantiene la firma de antes de S-03. */
    public RuleContext(SectorEntity sector, ZonaEntity zona, List<MetricSpec> specs, List<Metric> metrics,
                       ConfiguracionOperativaEntity config, String finalStatus, Instant now,
                       WeatherForecast forecast, boolean bloqueoManualActivo, ContextoRiego riego,
                       RuleOverrides overrides) {
        this(sector, zona, specs, metrics, config, finalStatus, now, forecast, bloqueoManualActivo, riego,
                overrides, Set.of(), HardwareCoverage.COMPLETE, HardwareFaults.NONE);
    }

    /** Constructor sin overrides: mantiene la firma de antes de los overrides de reglas. */
    public RuleContext(SectorEntity sector, ZonaEntity zona, List<MetricSpec> specs, List<Metric> metrics,
                       ConfiguracionOperativaEntity config, String finalStatus, Instant now,
                       WeatherForecast forecast, boolean bloqueoManualActivo, ContextoRiego riego) {
        this(sector, zona, specs, metrics, config, finalStatus, now, forecast, bloqueoManualActivo, riego,
                RuleOverrides.NONE, Set.of(), HardwareCoverage.COMPLETE, HardwareFaults.NONE);
    }

    /** Constructor sin contexto de riego: mantiene la firma de antes de las reglas de riego v2. */
    public RuleContext(SectorEntity sector, ZonaEntity zona, List<MetricSpec> specs, List<Metric> metrics,
                       ConfiguracionOperativaEntity config, String finalStatus, Instant now,
                       WeatherForecast forecast, boolean bloqueoManualActivo) {
        this(sector, zona, specs, metrics, config, finalStatus, now, forecast, bloqueoManualActivo,
                ContextoRiego.vacio());
    }

    /** Copia de este contexto con otros overrides. Lo usa el orquestador: el llamador no los conoce. */
    public RuleContext withOverrides(RuleOverrides nuevos) {
        return new RuleContext(sector, zona, specs, metrics, config, finalStatus, now, forecast,
                bloqueoManualActivo, riego, nuevos, invalidMetrics, hardware, faults);
    }

    /**
     * {@code false} si la métrica se descartó en este ciclo por lectura inválida (S-03). Una regla que decide con
     * ella debe leerla con {@link #validMetricRaw} (o preguntar esto) y no actuar si es {@code false}.
     */
    public boolean isMetricValid(String key) {
        return !invalidMetrics.contains(key);
    }

    /** Como {@link #metricRaw}, pero {@code null} (sin dato) si la métrica se descartó por lectura inválida. */
    public Double validMetricRaw(String key) {
        return isMetricValid(key) ? metricRaw(key) : null;
    }

    /**
     * Cooldown (en minutos) que reemplaza al configurado para {@code key} de la regla, si hay uno. {@code 0} es "sin
     * cooldown". Vacío en producción.
     */
    public Optional<Double> cooldownOverrideMinutes(String ruleName, String key) {
        return overrides.cooldownMinutes(ruleName, key);
    }

    /** {@code true} si la guarda {@code key} de la regla no se aplica. {@code false} en producción. */
    public boolean isBypassed(String ruleName, String key) {
        return overrides.isBypassed(ruleName, key);
    }

    /**
     * {@code true} si el sector tiene un diagnóstico de IA que todavía no usó ninguna evaluación: llegó
     * ({@code diagnosisTs}) después del último usado ({@code diagnosisUsadoTs}), o nunca se usó uno.
     */
    public boolean diagnosticoNuevo() {
        Long llegada = sector.getDiagnosisTs();
        Long usado = sector.getDiagnosisUsadoTs();
        return llegada != null && (usado == null || llegada > usado);
    }

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
