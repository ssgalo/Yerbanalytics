package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.model.RustificacionEtapaEntity;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Regla de control de mediasombra por plan de rustificación y radiación (HU-08).
 *
 * <p><b>Prioridad:</b> 12 — ejecutora, corre después de las reglas de riego e insumo.
 *
 * <p><b>Condición primaria — Plan de rustificación:</b> si existe un plan de etapas y la
 * apertura actual del sector difiere de la apertura prescrita para el día actual del ciclo,
 * emite {@code MOVER_MEDIASOMBRA} con el porcentaje objetivo.
 *
 * <p><b>Condición secundaria — Pico UV:</b> si el índice UV del pronóstico supera el
 * umbral configurado ({@code yerbanalytics.engine.uv-umbral}), se reduce la apertura
 * hasta el máximo protector configurado, sobrescribiendo el plan si es necesario.
 *
 * <p><b>Acción si no hay cambio:</b> {@code NOOP_INFO}.
 *
 * <p><b>Día del ciclo:</b> usa la fecha de siembra configurada en properties
 * ({@code yerbanalytics.nursery.fecha-siembra-iso}, formato {@code yyyy-MM-dd}).
 * Si no está configurada, el plan de rustificación se omite.
 */
@Component
public class MediasombraRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(MediasombraRule.class);
    private static final int PRIORITY = 12;
    private static final String NAME = "MediasombraRule";

    private final RustificacionEtapaRepository rustificacionRepository;
    private final double uvUmbral;
    private final String fechaSiembraIso;

    public MediasombraRule(
            RustificacionEtapaRepository rustificacionRepository,
            @Value("${yerbanalytics.engine.uv-umbral:7.0}") double uvUmbral,
            @Value("${yerbanalytics.nursery.fecha-siembra-iso:}") String fechaSiembraIso) {
        this.rustificacionRepository = rustificacionRepository;
        this.uvUmbral = uvUmbral;
        this.fechaSiembraIso = fechaSiembraIso;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx) {
        ConfiguracionOperativaEntity config = ctx.config();
        double maxApertura = config != null ? config.getMediasombraAperturaMaxPct() : 100.0;
        int aperturaActual = ctx.sector().getActuadorShade();

        // --- Condición secundaria: pico UV sobrescribe el plan ---
        WeatherForecast forecast = ctx.forecast();
        if (forecast != null && forecast.uvIndex() >= uvUmbral) {
            int aperturaProtectora = (int) Math.min(30.0, maxApertura);
            if (aperturaActual != aperturaProtectora) {
                String motivo = String.format(
                        "Pico de radiación UV detectado (índice %.1f ≥ umbral %.1f). " +
                        "Mediasombra reducida a %d%% para proteger plantines.",
                        forecast.uvIndex(), uvUmbral, aperturaProtectora);
                return List.of(moverMediasombra(aperturaProtectora, motivo));
            }
            return List.of(RuleAction.noopInfo(NAME,
                    String.format("Pico UV activo — mediasombra ya en posición protectora (%d%%).", aperturaActual)));
        }

        // --- Condición primaria: plan de rustificación ---
        if (fechaSiembraIso == null || fechaSiembraIso.isBlank()) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Fecha de siembra no configurada — plan de rustificación omitido."));
        }

        try {
            LocalDate siembra = LocalDate.parse(fechaSiembraIso);
            long diaCiclo = ChronoUnit.DAYS.between(siembra, LocalDate.now(ZoneId.systemDefault())) + 1;

            List<RustificacionEtapaEntity> etapas = rustificacionRepository.findAllByOrderByOrdenAsc();
            RustificacionEtapaEntity etapaActual = etapas.stream()
                    .filter(e -> diaCiclo >= e.getDiaDesde() && diaCiclo <= e.getDiaHasta())
                    .findFirst()
                    .orElse(null);

            if (etapaActual == null) {
                return List.of(RuleAction.noopInfo(NAME,
                        String.format("Día %d fuera del rango del plan de rustificación — sin cambio de mediasombra.", diaCiclo)));
            }

            int aperturaObjetivo = (int) Math.min(etapaActual.getAperturaPct(), maxApertura);
            if (aperturaActual == aperturaObjetivo) {
                return List.of(RuleAction.noopInfo(NAME,
                        String.format("Mediasombra ya en posición correcta para el día %d del ciclo (%d%%).",
                                diaCiclo, aperturaActual)));
            }

            String motivo = String.format(
                    "Plan de rustificación: día %d — apertura prescrita %d%% (actual: %d%%).",
                    diaCiclo, aperturaObjetivo, aperturaActual);
            return List.of(moverMediasombra(aperturaObjetivo, motivo));

        } catch (Exception e) {
            log.warn("MediasombraRule: error al evaluar plan de rustificación: {}", e.getMessage());
            return List.of(RuleAction.noopInfo(NAME, "Error al leer plan de rustificación — sin cambio."));
        }
    }

    private RuleAction moverMediasombra(int aperturaPct, String motivo) {
        // El porcentaje objetivo viaja en el motivo; el ActionExecutor lo parsea.
        String motivoConPct = "[apertura=" + aperturaPct + "] " + motivo;
        return RuleAction.of(ActionType.MOVER_MEDIASOMBRA, NAME, motivoConPct);
    }
}
