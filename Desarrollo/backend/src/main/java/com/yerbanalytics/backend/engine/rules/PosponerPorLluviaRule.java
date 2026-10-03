package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.riego.PrecondicionesRiego;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * R-03 · Posponer por lluvia ({@code reglas_v2} §5).
 *
 * <p>Cuando aplica R-01 y el pronóstico da, en las próximas {@code riego.lluvia-ventana} horas,
 * probabilidad horaria máxima ≥ {@code riego.lluvia-probabilidad} Y lluvia acumulada ≥
 * {@code riego.lluvia-mm}, emite {@code POSTPONE_RIEGO} y una alerta {@code INFO} por macro-zona. No hace
 * nada más: en la próxima lectura decide la regla que corresponda.
 *
 * <p>La condición (los dos umbrales sobre la lluvia prevista) vive en {@link PrecondicionesRiego#lluviaPospone}: el
 * {@code DespachoRiego} la revalida al abrir cada válvula, porque el pronóstico puede llegar DESPUÉS de que R-01
 * encoló la ronda (el primer mensaje tras arrancar no tiene pronóstico cacheado).
 *
 * <p>Sin pronóstico (o sin marcas horarias) las comparaciones quedan {@code SIN_DATO} y no pospone (O-01:
 * se asume que no llueve). Con déficit crítico no aplica: R-02 gana al clima (principio 3).
 */
@Component
public class PosponerPorLluviaRule implements Rule {

    private static final int PRIORITY = 8;
    private static final String NAME = "PosponerPorLluviaRule";
    static final String TEXTO_ALERTA = "Riego pospuesto por pronóstico de lluvia";

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
        return "🌧️ Posponer por lluvia (R-03)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.RIEGO;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosRiego.LLUVIA_PROBABILIDAD, ParametrosRiego.LLUVIA_MM, ParametrosRiego.LLUVIA_VENTANA,
                ParametrosRiego.UMBRAL_HUMEDAD, ParametrosRiego.UMBRAL_CRITICO);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Double humedad = RiegoRuleSupport.humedad(ctx);
        if (!RiegoRuleSupport.aplicaR01(ev, humedad)) {
            return List.of(RuleAction.noopInfo(NAME, RiegoRuleSupport.noAplica(humedad,
                    ev.numero(ParametrosRiego.UMBRAL_CRITICO), ev.numero(ParametrosRiego.UMBRAL_HUMEDAD))));
        }

        int horas = (int) ev.numero(ParametrosRiego.LLUVIA_VENTANA);
        WeatherForecast pronostico = ctx.forecast();
        WeatherForecast.LluviaPrevista lluvia =
                PrecondicionesRiego.lluviaPrevista(pronostico, RiegoRuleSupport.fechaHoraLocal(ctx), horas);
        // Una hora sin dato no es una hora seca: si falta la probabilidad o los milímetros, esa comparación
        // queda SIN_DATO y no se pospone (O-01: sin pronóstico se asume que no llueve).
        Double probMax = lluvia == null ? null : lluvia.probMaxPct();
        Double mmTotal = lluvia == null ? null : lluvia.mmTotal();

        boolean probable = ev.comparar("Probabilidad máxima de lluvia en las próximas " + horas + " h",
                probMax, Operador.GE, ParametrosRiego.LLUVIA_PROBABILIDAD);
        boolean abundante = ev.comparar("Lluvia acumulada en las próximas " + horas + " h",
                mmTotal, Operador.GE, ParametrosRiego.LLUVIA_MM);

        if (probMax == null || mmTotal == null) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Pronóstico de lluvia incompleto o no disponible: se asume que no llueve y el riego sigue su curso."));
        }
        if (probable && abundante) {   // == PrecondicionesRiego.lluviaPospone, con la traza de cada umbral
            String detalle = String.format("probabilidad máxima %s%% y %s mm en las próximas %d h",
                    RiegoRuleSupport.num(probMax), RiegoRuleSupport.num(mmTotal), horas);
            RuleAction pospone = RuleAction.of(ActionType.POSTPONE_RIEGO, NAME,
                    "Riego pospuesto por lluvia prevista (" + detalle + "). En la próxima lectura se decide de nuevo.");
            RuleAction alerta = RuleAction.of(ActionType.ALERTA, NAME, "Lluvia prevista: " + detalle + ".",
                    new DetalleAlerta(NivelAlerta.INFO, TEXTO_ALERTA + " (" + detalle + ")"));
            return List.of(pospone, alerta);
        }
        return List.of(RuleAction.noopInfo(NAME, String.format(
                "Lluvia prevista insuficiente para posponer (probabilidad máxima %s%%, %s mm en %d h).",
                RiegoRuleSupport.num(probMax), RiegoRuleSupport.num(mmTotal), horas)));
    }
}
