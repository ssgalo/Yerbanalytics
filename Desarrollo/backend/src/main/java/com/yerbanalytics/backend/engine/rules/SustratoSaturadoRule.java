package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.CancelaRiego;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
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
 * R-04 · Sustrato saturado ({@code reglas_v2} §5).
 *
 * <p>Con humedad de sustrato ≥ {@code riego.saturacion-bloqueo} (75 %) bloquea todo riego autónomo del
 * sector ({@code ABORT_RIEGO}); con ≥ {@code riego.saturacion-alerta} (80 %) además emite una alerta
 * {@code WARNING} por macro-zona. Sin lectura de humedad no bloquea (la frescura la cuida
 * {@code StaleSensorRule}).
 *
 * <p>Va antes que R-02: son excluyentes (una sola humedad por macro-zona y {@code bloqueo > crítico}
 * por rango), así que nunca le quita el paso.
 */
@Component
public class SustratoSaturadoRule implements Rule {

    private static final int PRIORITY = 3;
    private static final String NAME = "SustratoSaturadoRule";
    static final String TEXTO_ALERTA = "Sustrato saturado, riesgo de asfixia radicular y hongos";

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
        return "💧 Sustrato saturado (R-04)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.RIEGO;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosRiego.SATURACION_BLOQUEO, ParametrosRiego.SATURACION_ALERTA);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Double humedad = RiegoRuleSupport.humedad(ctx);
        boolean saturado = ev.comparar("Humedad de sustrato", humedad, Operador.GE, ParametrosRiego.SATURACION_BLOQUEO);

        if (humedad == null || humedad.isNaN()) {
            return List.of(RuleAction.noopInfo(NAME, "Sin lectura de humedad de sustrato: no se evalúa la saturación."));
        }
        if (!saturado) {
            return List.of(RuleAction.noopInfo(NAME, String.format(
                    "Humedad de sustrato %s%% bajo el umbral de saturación (%s%%): no se bloquea el riego.",
                    RiegoRuleSupport.num(humedad), RiegoRuleSupport.num(ev.numero(ParametrosRiego.SATURACION_BLOQUEO)))));
        }

        RuleAction aborta = RuleAction.of(ActionType.ABORT_RIEGO, NAME, String.format(
                "Humedad de sustrato %s%% en o sobre el umbral de saturación (%s%%): riego autónomo bloqueado.",
                RiegoRuleSupport.num(humedad), RiegoRuleSupport.num(ev.numero(ParametrosRiego.SATURACION_BLOQUEO))),
                CancelaRiego.TODAS);

        boolean conAlerta = ev.comparar("Humedad de sustrato", humedad, Operador.GE, ParametrosRiego.SATURACION_ALERTA);
        if (!conAlerta) {
            return List.of(aborta);
        }
        RuleAction alerta = RuleAction.of(ActionType.ALERTA, NAME, String.format(
                "Humedad de sustrato %s%% en o sobre el umbral de alerta (%s%%).",
                RiegoRuleSupport.num(humedad), RiegoRuleSupport.num(ev.numero(ParametrosRiego.SATURACION_ALERTA))),
                new DetalleAlerta(NivelAlerta.WARNING, TEXTO_ALERTA));
        return List.of(aborta, alerta);
    }
}
