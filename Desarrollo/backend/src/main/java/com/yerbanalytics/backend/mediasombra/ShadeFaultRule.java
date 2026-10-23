package com.yerbanalytics.backend.mediasombra;

import com.yerbanalytics.backend.engine.RuleEffect;
import java.util.Optional;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.MallaZona;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.zone.ZoneRule;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.mqtt.NodeErrorType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * La guarda de la rama MEDIASOMBRA, que es de la macro-zona: la malla no recibe comandos del motor si la zona tiene un
 * bloqueo manual (S-01), si no tiene mediasombra registrada (S-04) o si la tiene marcada como averiada (S-05: el nodo
 * informó {@code MEDIASOMBRA_FIN_DE_CARRERA}, o el ack {@code falla_mecanica}) hasta que alguien registre la reparación.
 *
 * <p><b>Prioridad:</b> 13 — antes de la regla que mueve la malla ({@link ShadingRule}).
 *
 * <p><b>Acción:</b> {@code ABORT_MEDIASOMBRA}: corta sólo la rama de la malla en la zona. Sin impedimento,
 * {@code NOOP_INFO}. No emite alertas (la de la falla sale una sola vez, cuando el nodo la informa).
 *
 * <p>Las mismas guardas las aplica, por su cuenta, el horario de la mediasombra ({@link HorarioMediasombraService}).
 *
 * <p><b>Simulador:</b> el bypass {@code fault} ignora la falla marcada; {@code hardware}, la falta de mediasombra.
 */
@Component
public class ShadeFaultRule implements ZoneRule {

    private static final int PRIORITY = 13;
    public static final String NAME = "ShadeFaultRule";
    /** Bypass key: evaluates the zone as if its shade mesh were healthy. */
    public static final String BYPASS_FAULT = "fault";
    /** Bypass key: evaluates the zone as if it had its shade mesh registered. */
    public static final String BYPASS_HARDWARE = "hardware";

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
        return "Mediasombra disponible";
    }

    @Override
    public String code() {
        return "S-01, S-04, S-05";
    }

    @Override
    public RuleEffect effect() {
        return RuleEffect.PROTECTS;
    }

    @Override
    public Optional<String> protection() {
        return Optional.of("Se mueve la malla de una zona bloqueada, sin malla registrada o con la malla averiada.");
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.MEDIASOMBRA;
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_FAULT, BYPASS_HARDWARE);
    }

    @Override
    public List<RuleAction> evaluate(ZoneRuleContext ctx, Evaluacion ev) {
        MallaZona malla = ctx.malla();
        boolean registrada = malla.registrada() || ctx.isBypassed(NAME, BYPASS_HARDWARE);
        boolean sana = !malla.tieneFalla() || ctx.isBypassed(NAME, BYPASS_FAULT);

        if (!ev.compararFijo("Macro-zona sin bloqueo manual", !ctx.zoneLocked(), Operador.EQ, true)) {
            return abort("Bloqueo manual vigente en la macro-zona: el motor no mueve la mediasombra.");
        }
        if (!ev.compararFijo("Mediasombra de la macro-zona registrada", registrada, Operador.EQ, true)) {
            return abort("Hardware incompleto: la macro-zona no tiene mediasombra registrada. "
                    + "El control de la malla queda anulado.");
        }
        if (!ev.compararFijo("Mediasombra de la macro-zona sin falla mecánica", sana, Operador.EQ, true)) {
            return abort("Falla mecánica en la mediasombra de la macro-zona (" + NodeErrorType.describe(malla.falla())
                    + "). La malla no recibirá comandos hasta registrar la reparación.");
        }
        return List.of(RuleAction.noopInfo(NAME, "La mediasombra de la macro-zona está registrada, sana y sin bloqueo."));
    }

    private static List<RuleAction> abort(String motivo) {
        return List.of(RuleAction.of(ActionType.ABORT_MEDIASOMBRA, NAME, motivo));
    }
}
