package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.riego.CalculoRiego;
import com.yerbanalytics.backend.engine.riego.SolicitudRiego;
import com.yerbanalytics.backend.engine.traza.Evaluacion;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * R-01 · Riego por déficit hídrico ({@code reglas_v2} §5). La regla ejecutora de la rama.
 *
 * <p>Con {@code riego.umbral-critico ≤ humedad < riego.umbral-humedad}, y si ninguna compuerta (R-04,
 * ciclo, R-05, R-06, R-03) cortó antes, ordena regar con
 * {@code V = min((objetivo − h) × litros por punto, volumen máximo)}; el tiempo sale del caudal del emisor.
 * Con déficit crítico no riega: ya lo ordenó R-02 y una segunda válvula no tiene sentido.
 *
 * <p>No emite una orden vacía: si el volumen redondea a 0 L (duración 0 s) queda como {@code NOOP_INFO}.
 */
@Component
public class RiegoPorDeficitRule implements Rule {

    private static final int PRIORITY = 10;
    private static final String NAME = SolicitudRiego.REGLA_DEFICIT_COMUN;

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
        return "💦 Riego por déficit hídrico (R-01)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.RIEGO;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosRiego.UMBRAL_HUMEDAD, ParametrosRiego.UMBRAL_CRITICO, ParametrosRiego.HUMEDAD_OBJETIVO,
                ParametrosRiego.LITROS_POR_PUNTO, ParametrosRiego.VOLUMEN_MAX_EVENTO, ParametrosRiego.CAUDAL_EMISOR);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Double humedad = RiegoRuleSupport.humedad(ctx);
        double umbral = ev.numero(ParametrosRiego.UMBRAL_HUMEDAD);
        double critico = ev.numero(ParametrosRiego.UMBRAL_CRITICO);
        if (!RiegoRuleSupport.aplicaR01(ev, humedad)) {
            return List.of(RuleAction.noopInfo(NAME, humedad != null && !humedad.isNaN() && humedad < critico
                    ? String.format("Humedad %s%% bajo el umbral crítico: lo cubre R-02 (déficit crítico).",
                            RiegoRuleSupport.num(humedad))
                    : RiegoRuleSupport.noAplica(humedad, critico, umbral)));
        }

        CalculoRiego.PlanRiego plan = CalculoRiego.porDeficit(humedad,
                ev.numero(ParametrosRiego.HUMEDAD_OBJETIVO), ev.numero(ParametrosRiego.LITROS_POR_PUNTO),
                ev.numero(ParametrosRiego.VOLUMEN_MAX_EVENTO), ev.numero(ParametrosRiego.CAUDAL_EMISOR));
        if (plan.duracionSeg() < 1) {
            return List.of(RuleAction.noopInfo(NAME, String.format(
                    "Humedad de sustrato %s%% bajo el umbral, pero el volumen calculado es 0 L: no se riega.",
                    RiegoRuleSupport.num(humedad))));
        }
        return List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, NAME, String.format(
                "Humedad de sustrato %s%% bajo el umbral de riego (%s%%). Regar %s hasta la humedad objetivo%s.",
                RiegoRuleSupport.num(humedad), RiegoRuleSupport.num(umbral),
                RiegoRuleSupport.volumenYTiempo(plan.volumenL(), plan.duracionSeg()),
                plan.recortado() ? " (duración recortada al máximo de la válvula)" : ""),
                plan.aDetalle(humedad)));
    }
}
