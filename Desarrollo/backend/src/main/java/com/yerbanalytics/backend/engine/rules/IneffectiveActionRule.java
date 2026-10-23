package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.effectiveness.EffectivenessBlockService;
import com.yerbanalytics.backend.effectiveness.EffectivenessKind;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.CancelaRiego;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * S-06 · Acción sin efectividad ({@code reglas_v2} §4), el lado del riego.
 *
 * <p>Si el último riego de la macro-zona quedó «sin efectividad» (la humedad no subió lo suficiente tras la lectura de
 * verificación, E-01) y nadie registró la revisión, corta la rama de riego de TODOS los sectores de la zona: ni R-01 ni la
 * reposición del estrés solar riegan hasta que alguien revise en persona qué pasa (una válvula tapada, un emisor sin presión).
 * También retira lo que esperaba en la cola de riego, salvo el déficit crítico.
 *
 * <p><b>Prioridad 7: después de R-02.</b> El déficit crítico sigue pudiendo regar (la v2 lo exceptúa: no se deja secar el
 * sustrato por un bloqueo), con el tope de un riego cada {@code riego.exceptuado-bloqueo} por sector que R-02 aplica siempre
 * (ver su javadoc). Y antes de la ventana, la pausa y R-01, a los que les corta el paso.
 *
 * <p>El bloqueo de los <i>tratamientos</i> (el fertilizante correctivo y el fitosanitario sin efectividad) no es de esta regla:
 * lo aplican las precondiciones de aplicación ({@code ApplicationPreconditions}), porque la aplicación pasa siempre por ahí. La
 * mediasombra no se evalúa y por lo tanto nunca se bloquea. El aviso {@code CRITICAL} lo emite el servicio de efectividad una
 * vez, al dar el veredicto.
 *
 * <p>Bypass {@value #BYPASS_BLOCK}: el riego sigue aunque esté bloqueado (control de demo).
 */
@Component
public class IneffectiveActionRule implements Rule {

    private static final int PRIORITY = 7;
    public static final String NAME = EffectivenessBlockService.RULE_NAME;
    public static final String BYPASS_BLOCK = EffectivenessBlockService.BYPASS_BLOCK;

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
        return "🚧 Acción sin efectividad (S-06)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.RIEGO;
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_BLOCK);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        if (!ctx.effectiveness().blocks(EffectivenessKind.IRRIGATION)) {
            return List.of(RuleAction.noopInfo(NAME, "El último riego de la macro-zona no quedó sin efectividad: no se bloquea."));
        }
        if (ctx.isBypassed(NAME, BYPASS_BLOCK)) {
            return List.of(RuleAction.noopInfo(NAME, "S-06: el riego de la macro-zona está bloqueado por falta de efectividad, "
                    + "pero el control de demo saltea el bloqueo."));
        }
        return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, String.format(
                "S-06: el último riego de %s quedó sin efectividad; el riego autónomo (salvo el déficit crítico, R-02) "
                        + "queda bloqueado hasta que se registre la revisión física.",
                ctx.zona().getId()), CancelaRiego.SOLO_DEFICIT_COMUN));
    }
}
