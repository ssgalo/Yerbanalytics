package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regla de seguridad por antigüedad de telemetría (HU-02 CA-03/04).
 *
 * <p><b>Prioridad:</b> 1 (segunda en correr, después de {@link BloqueoManualRule}).
 *
 * <p><b>Condición:</b> {@link RuleContext#sensorStale()} es {@code true}, es decir,
 * el nodo testigo de la macro-zona no reportó dentro del umbral configurado en
 * {@code yerbanalytics.nursery.stale-threshold-ms}.
 *
 * <p><b>Acción si se cumple:</b> {@code ABORT_RIEGO} — el motor detiene toda actuación
 * de riego para el sector. Regar sin lectura válida puede causar encharcamiento o
 * daño físico a los plantines.
 *
 * <p><b>Acción si no se cumple:</b> {@code NOOP_INFO} — confirmación de que el sensor
 * reportó a tiempo; la cadena continúa evaluando las reglas ejecutoras.
 */
@Component
public class StaleSensorRule implements Rule {

    private static final int PRIORITY = 1;
    private static final String NAME = "StaleSensorRule";

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
        if (ctx.sensorStale()) {
            String zonaId = ctx.zona() != null ? ctx.zona().getId() : "desconocida";
            String motivo = String.format(
                    "El nodo testigo de la macro-zona %s no reportó dentro del umbral " +
                    "de antigüedad configurado. Actuación autónoma anulada por seguridad.",
                    zonaId);
            return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, motivo));
        }

        return List.of(RuleAction.noopInfo(NAME,
                "Telemetría fresca: el nodo reportó dentro del umbral configurado."));
    }
}
