package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
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

    private final ConfiguracionService configuracionService;

    public RiegoRule(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
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

        // Sin lectura disponible: no actuar (el StaleSensorRule lo manejará en R1)
        if (humSus == null) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Sin lectura de humedad de sustrato — no se puede evaluar riego."));
        }

        if (humSus < umbral) {
            String motivo = String.format("Humedad de sustrato %.0f%% bajo el umbral mínimo de %.0f%%.",
                    humSus, umbral);
            return List.of(RuleAction.of(
                    com.yerbanalytics.backend.engine.ActionType.ACTIVAR_VALVULA, NAME, motivo));
        } else {
            String motivo = String.format(
                    "Humedad de sustrato %.0f%% dentro del rango aceptable (umbral: %.0f%%). No se riega.",
                    humSus, umbral);
            return List.of(RuleAction.noopInfo(NAME, motivo));
        }
    }

    private double getUmbral() {
        try {
            return configuracionService.getRiegoHumSusUmbral();
        } catch (RuntimeException e) {
            return DEFAULT_HUM_UMBRAL;
        }
    }
}
