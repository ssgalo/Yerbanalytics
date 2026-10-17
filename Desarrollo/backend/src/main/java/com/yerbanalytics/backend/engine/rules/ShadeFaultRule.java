package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.HardwareFaults;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.mqtt.NodeErrorType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * S-05: con la mediasombra de la macro-zona marcada como averiada (el nodo informó
 * {@code MEDIASOMBRA_FIN_DE_CARRERA}, o el ack {@code falla_mecanica}) la malla no recibe comandos nuevos hasta que
 * alguien registre la reparación.
 *
 * <p><b>Prioridad:</b> 13 — dentro de la rama de la mediasombra y antes de la regla que mueve la malla
 * ({@code ShadingRule}), que es la única que hoy lo hace.
 *
 * <p><b>Acción:</b> {@code ABORT_MEDIASOMBRA}: corta sólo la rama de la malla; riego e insumos no se tocan. Sin falla,
 * {@code NOOP_INFO}. No emite alertas (la alerta sale una sola vez, cuando el nodo informa la falla).
 *
 * <p><b>Simulador:</b> el bypass {@code fault} ignora la falla marcada.
 */
@Component
public class ShadeFaultRule implements Rule {

    private static final int PRIORITY = 13;
    public static final String NAME = "ShadeFaultRule";
    /** Bypass key: evaluates the zone as if its shade mesh were healthy. */
    public static final String BYPASS_FAULT = "fault";

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "⚙️ Falla mecánica en mediasombra (S-05)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.MEDIASOMBRA;
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_FAULT);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        HardwareFaults faults = ctx.isBypassed(NAME, BYPASS_FAULT) ? HardwareFaults.NONE : ctx.faults();
        if (ev.compararFijo("Mediasombra de la macro-zona sin falla mecánica", !faults.hasShade(), Operador.EQ, true)) {
            return List.of(RuleAction.noopInfo(NAME, "Sin falla mecánica: la mediasombra de la macro-zona está sana."));
        }

        return List.of(RuleAction.of(ActionType.ABORT_MEDIASOMBRA, NAME,
                "Falla mecánica en la mediasombra de la macro-zona (" + NodeErrorType.describe(faults.shade()) + "). "
                        + "La malla no recibirá comandos hasta registrar la reparación."));
    }
}
