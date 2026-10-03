package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.traza.Evaluacion;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * R-05 · Riego fuera de ventana ({@code reglas_v2} §5).
 *
 * <p>Fuera de {@code riego.ventana-normal} (06:00-18:00 hora local del vivero), cuando aplica R-01,
 * emite {@code ABORT_RIEGO}: de noche sólo puede regar R-02 (follaje mojado toda la noche = hongos). La
 * ventana incluye el minuto de su hora de fin: la lectura de las 18:00 todavía entra (18:00:59 adentro,
 * 18:01:00 afuera).
 */
@Component
public class FueraDeVentanaRiegoRule implements Rule {

    private static final int PRIORITY = 6;
    private static final String NAME = "FueraDeVentanaRiegoRule";

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
        return "🌙 Riego fuera de ventana horaria (R-05)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.RIEGO;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosRiego.VENTANA_NORMAL, ParametrosRiego.UMBRAL_HUMEDAD, ParametrosRiego.UMBRAL_CRITICO);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Double humedad = RiegoRuleSupport.humedad(ctx);
        if (!RiegoRuleSupport.aplicaR01(ev, humedad)) {
            return List.of(RuleAction.noopInfo(NAME, RiegoRuleSupport.noAplica(humedad,
                    ev.numero(ParametrosRiego.UMBRAL_CRITICO), ev.numero(ParametrosRiego.UMBRAL_HUMEDAD))));
        }
        boolean dentro = ev.compararVentana("Hora local", RiegoRuleSupport.horaLocal(ctx), ParametrosRiego.VENTANA_NORMAL);
        if (!dentro) {
            return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, String.format(
                    "Fuera de la ventana de riego (%s): el riego por déficit espera a la próxima lectura dentro de ella. "
                            + "Sólo el déficit crítico riega de noche.",
                    ev.ventana(ParametrosRiego.VENTANA_NORMAL).canonico())));
        }
        return List.of(RuleAction.noopInfo(NAME, "Dentro de la ventana de riego."));
    }
}
