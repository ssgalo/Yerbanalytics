package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.CancelaRiego;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * R-06 · Pausa después de una aplicación ({@code reglas_v2} §5).
 *
 * <p>Cuando aplica R-01 y al sector se le aplicó fertilizante o fitosanitario hace menos de
 * {@code riego.pausa-tras-aplicacion} horas, emite {@code ABORT_RIEGO} para ese sector (el agua lavaría el
 * producto). Los demás sectores de la macro-zona siguen su curso; R-02 sí puede regarlo.
 */
@Component
public class PausaTrasAplicacionRule implements Rule {

    private static final int PRIORITY = 7;
    private static final String NAME = "PausaTrasAplicacionRule";

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
        return "⏸️ Pausa tras una aplicación (R-06)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.RIEGO;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosRiego.PAUSA_TRAS_APLICACION, ParametrosRiego.UMBRAL_HUMEDAD, ParametrosRiego.UMBRAL_CRITICO);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Double humedad = RiegoRuleSupport.humedad(ctx);
        if (!RiegoRuleSupport.aplicaR01(ev, humedad)) {
            return List.of(RuleAction.noopInfo(NAME, RiegoRuleSupport.noAplica(humedad,
                    ev.numero(ParametrosRiego.UMBRAL_CRITICO), ev.numero(ParametrosRiego.UMBRAL_HUMEDAD))));
        }
        Long aplicacion = ctx.riego().ultimaAplicacionMs();
        if (aplicacion == null) {
            return List.of(RuleAction.noopInfo(NAME, "El sector no tiene aplicaciones recientes de insumo."));
        }
        double horas = RiegoRuleSupport.horasDesde(aplicacion, ctx.now());
        if (ev.comparar("Horas desde la última aplicación de insumo", horas, Operador.LT,
                ParametrosRiego.PAUSA_TRAS_APLICACION)) {
            return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, String.format(
                    "Al sector se le aplicó un insumo a las %s (hace menos de %s h): no se riega para no lavar el producto.",
                    RiegoRuleSupport.hora(aplicacion), RiegoRuleSupport.num(ev.numero(ParametrosRiego.PAUSA_TRAS_APLICACION))),
                    CancelaRiego.SOLO_DEFICIT_COMUN));
        }
        return List.of(RuleAction.noopInfo(NAME, "La pausa tras la última aplicación de insumo ya se cumplió."));
    }
}
