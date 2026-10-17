package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.CancelaRiego;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.riego.PrecondicionesRiego;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * R-06 · Pausa después de una aplicación ({@code reglas_v2} §5).
 *
 * <p>Cuando aplica R-01 y al sector se le aplicó fertilizante o fitosanitario hace menos de
 * {@code riego.pausa-tras-aplicacion} horas, emite {@code ABORT_RIEGO} para ese sector (el agua lavaría el
 * producto). Los demás sectores de la macro-zona siguen su curso; R-02 sí puede regarlo. La condición vive en
 * {@link PrecondicionesRiego#enPausaPorAplicacion}: el despacho la revalida, porque una dosificación puede llegar
 * mientras el sector espera su tanda con R-01 encolado.
 */
@Component
public class PausaTrasAplicacionRule implements Rule {

    private static final int PRIORITY = 9;
    public static final String NAME = "PausaTrasAplicacionRule";
    /** Clave de cooldown: minutos de pausa tras una aplicación de insumo (0 la desactiva). */
    public static final String COOLDOWN_PAUSE = "pause";

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
    public java.util.Set<String> cooldownKeys() {
        return java.util.Set.of(COOLDOWN_PAUSE);
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
        java.util.Optional<Double> pausaOverride = ctx.cooldownOverrideMinutes(NAME, COOLDOWN_PAUSE);
        boolean enPausa;
        if (pausaOverride.isPresent()) {
            enPausa = pausaOverride.get() > 0 && ev.compararFijo("Minutos desde la última aplicación de insumo",
                    horas * 60.0, Operador.LT, pausaOverride.get());
        } else {
            enPausa = ev.comparar("Horas desde la última aplicación de insumo", horas, Operador.LT,
                    ParametrosRiego.PAUSA_TRAS_APLICACION);
        }
        if (enPausa) {
            return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, String.format(
                    "Al sector se le aplicó un insumo a las %s (hace menos de %s h): no se riega para no lavar el producto.",
                    RiegoRuleSupport.hora(aplicacion), pausaOverride.isPresent()
                            ? RiegoRuleSupport.num(pausaOverride.get() / 60.0)
                            : RiegoRuleSupport.num(ev.numero(ParametrosRiego.PAUSA_TRAS_APLICACION))),
                    CancelaRiego.SOLO_DEFICIT_COMUN));
        }
        return List.of(RuleAction.noopInfo(NAME, "La pausa tras la última aplicación de insumo ya se cumplió."));
    }
}
