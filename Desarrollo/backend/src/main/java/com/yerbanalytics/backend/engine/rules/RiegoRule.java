package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.service.ConfiguracionService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regla de riego autónomo por humedad de sustrato (HU-06).
 *
 * <p><b>Prioridad:</b> 10 (ejecutora).
 *
 * <p><b>Condición:</b> humedad de sustrato actual menor al umbral configurado
 * en {@link ConfiguracionService#getRiegoHumSusUmbral()}.
 *
 * <p><b>Guards de límites operativos (HU-15 CA-04):</b>
 * <ul>
 *   <li>Si el sector ya regó en las últimas 24 h, se bloquea por volumen diario.</li>
 *   <li>El tiempo máximo de riego ({@code riegoTiempoMaxSeg}) se adjunta al motivo
 *       para que el {@code ActionExecutor} lo considere al enviar el comando MQTT.</li>
 * </ul>
 *
 * <p><b>Acción si se cumple:</b> {@code ACTIVAR_VALVULA} — el {@link com.yerbanalytics.backend.engine.ActionExecutor}
 * abrirá la electroválvula y registrará el riego en el historial.
 *
 * <p><b>Acción si no se cumple:</b> {@code NOOP_INFO} — Registro de Inacción
 * para que el usuario sepa que el sistema evaluó la condición y no la encontró necesaria.
 */
@Component
public class RiegoRule implements Rule {

    private static final int PRIORITY = 10;
    private static final String NAME = "RiegoRule";
    private static final double DEFAULT_HUM_UMBRAL = 40.0;
    private static final long MS_EN_24H = 24L * 60 * 60 * 1000;

    private final ConfiguracionService configuracionService;
    private final HistorialRepository historialRepository;

    public RiegoRule(ConfiguracionService configuracionService,
                     HistorialRepository historialRepository) {
        this.configuracionService = configuracionService;
        this.historialRepository = historialRepository;
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
        Double humSus = ctx.metricRaw("humSus");
        double umbral = getUmbral();

        // Sin lectura disponible: no actuar (el StaleSensorRule lo manejará)
        if (humSus == null) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Sin lectura de humedad de sustrato — no se puede evaluar riego."));
        }

        if (humSus >= umbral) {
            String motivo = String.format(
                    "Humedad de sustrato %.0f%% dentro del rango aceptable (umbral: %.0f%%). No se riega.",
                    humSus, umbral);
            return List.of(RuleAction.noopInfo(NAME, motivo));
        }

        // Guard de límite operativo: verificar volumen diario (HU-15 CA-04)
        ConfiguracionOperativaEntity config = ctx.config();
        if (config != null) {
            long desde = System.currentTimeMillis() - MS_EN_24H;
            long riegosEn24h = historialRepository.countByTipoAndSectorAndPeriod(
                    ctx.sector().getId(), "Riego", desde);

            if (riegosEn24h > 0) {
                String motivoBloqueo = String.format(
                        "Humedad de sustrato %.0f%% bajo el umbral (%.0f%%), pero el sector %s " +
                        "ya recibió %d riego(s) en las últimas 24 h (volumen diario máx: %.0f ml). " +
                        "Riego autónomo bloqueado por límite operativo.",
                        humSus, umbral, ctx.sector().getId(), riegosEn24h, config.getRiegoVolMaxDiarioMl());
                return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, motivoBloqueo));
            }

            // Adjuntar tiempo máximo al motivo para el ActionExecutor (futura implementación MQTT)
            String motivo = String.format(
                    "Humedad de sustrato %.0f%% bajo el umbral mínimo de %.0f%%. " +
                    "[tiempo-max-seg=%.0f]",
                    humSus, umbral, config.getRiegoTiempoMaxSeg());
            return List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, NAME, motivo));
        }

        // Sin configuración: actuar con motivo simple
        String motivo = String.format("Humedad de sustrato %.0f%% bajo el umbral mínimo de %.0f%%.",
                humSus, umbral);
        return List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, NAME, motivo));
    }

    private double getUmbral() {
        try {
            return configuracionService.getRiegoHumSusUmbral();
        } catch (RuntimeException e) {
            return DEFAULT_HUM_UMBRAL;
        }
    }
}
