package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.CancelaRiego;
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
 * S-05: un sector cuya electroválvula quedó marcada como averiada (el nodo informó {@code SIN_CAUDAL} o
 * {@code CAUDAL_EXCESIVO}) sale del riego y de la dosificación autónomos hasta que alguien registre la reparación.
 *
 * <p><b>Prioridad:</b> 2 — junto a las demás condiciones de la instalación y antes de cualquier regla que ordene
 * agua. Tiene que ir antes que R-02: la v2 no exceptúa a la falla hidráulica, así que el déficit crítico tampoco riega
 * un sector averiado (por eso la alerta que deja el nodo pide riego manual).
 *
 * <p><b>Acciones:</b> {@code ABORT_RIEGO} con {@link CancelaRiego#TODAS} (también retira el riego que ya estaba encolado:
 * el nodo ya cortó la válvula y abrir otra vez sería insistir contra la falla) y {@code ABORT_INSUMO} (el insumo viaja
 * por la misma línea de agua). Nunca {@code ABORT_ALL}: la malla es de la macro-zona y los demás sectores siguen. Sin
 * falla, {@code NOOP_INFO}. No emite alertas: la alerta {@code CRITICAL} sale una sola vez, cuando el nodo informa la falla.
 *
 * <p><b>Simulador:</b> el bypass {@code fault} ignora la falla marcada (el sector se evalúa como si estuviera sano).
 */
@Component
public class HydraulicFaultRule implements Rule {

    private static final int PRIORITY = 2;
    public static final String NAME = "HydraulicFaultRule";
    /** Bypass key: evaluates the sector as if its valve were healthy. */
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
        return "🚰 Falla hidráulica (S-05)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.GLOBAL;
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_FAULT);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        HardwareFaults faults = ctx.isBypassed(NAME, BYPASS_FAULT) ? HardwareFaults.NONE : ctx.faults();
        if (ev.compararFijo("Electroválvula del sector sin falla hidráulica", !faults.hasHydraulic(), Operador.EQ, true)) {
            return List.of(RuleAction.noopInfo(NAME, "Sin falla hidráulica: la electroválvula del sector está sana."));
        }

        String motivo = "Falla hidráulica en el sector " + ctx.sector().getId() + " ("
                + NodeErrorType.describe(faults.hydraulic()) + "): su electroválvula está marcada como averiada. "
                + "El riego y la dosificación autónomos del sector quedan anulados hasta registrar la reparación.";
        return List.of(
                RuleAction.of(ActionType.ABORT_RIEGO, NAME, motivo, CancelaRiego.TODAS),
                RuleAction.of(ActionType.ABORT_INSUMO, NAME, motivo));
    }
}
