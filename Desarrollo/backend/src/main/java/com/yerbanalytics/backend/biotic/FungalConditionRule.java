package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosBiotico;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.zone.ZoneRule;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.readings.ReadingHistory;
import com.yerbanalytics.backend.readings.ReadingMetric;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * F-F2 · Condición predisponente al hongo (reglas_v2 §8.5): con el DPV por debajo de {@code biotico.dpv-saturado} en dos
 * lecturas de pulso seguidas, la temperatura del aire entre {@code biotico.temp-predisponente-min} y {@code -max} y el
 * sustrato en {@code biotico.humedad-predisponente} o más, avisa con un {@code WARNING} «Condiciones favorables a
 * enfermedades fúngicas». <b>No aplica nada</b>: es prevención, y funciona sin diagnóstico (D-5).
 *
 * <p><b>Es de la macro-zona.</b> El DPV, la temperatura y el sustrato son de la lectura del nodo testigo, así que es una
 * {@link ZoneRule}: corre una vez por zona al cierre del pulso con motor.
 *
 * <p><b>Una alerta por evento.</b> La condición sigue cierta en cada pulso mientras dure el ambiente, pero el evento ya se
 * avisó (principio 8 de la v2). La regla no guarda estado: avisa sólo cuando la condición se cumple con las últimas
 * lecturas y <i>no</i> se cumplía con las mismas lecturas corridas una hacia atrás, es decir, cuando el evento empieza.
 *
 * <p><b>Simulador:</b> el bypass {@value #BYPASS_READINGS} toma una sola lectura de DPV bajo el umbral, sin exigir dos seguidas.
 */
@Component
public class FungalConditionRule implements ZoneRule {

    /** Prioridad: única entre las reglas por sector y por macro-zona; antes que el plan de nutrición (sanidad antes que rutina). */
    private static final int PRIORITY = 17;
    public static final String NAME = "FungalConditionRule";
    /** Bypass key: a single low-DPV reading is enough. */
    public static final String BYPASS_READINGS = "readings";

    /** Las lecturas «seguidas» que pide la v2. */
    private static final int CONSECUTIVE_READINGS = 2;

    private final ReadingHistory readings;

    public FungalConditionRule(ReadingHistory readings) {
        this.readings = readings;
    }

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
        return "💧 Condición predisponente al hongo (F-F2)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.INSUMO;
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_READINGS);
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosBiotico.DPV_SATURATED, ParametrosBiotico.PREDISPOSING_TEMP_MIN,
                ParametrosBiotico.PREDISPOSING_TEMP_MAX, ParametrosBiotico.PREDISPOSING_MOISTURE);
    }

    @Override
    public List<RuleAction> evaluate(ZoneRuleContext ctx, Evaluacion ev) {
        String zoneId = ctx.zone().getId();
        int required = ctx.isBypassed(NAME, BYPASS_READINGS) ? 1 : CONSECUTIVE_READINGS;

        // Una lectura de más para saber si la condición ya se cumplía antes de la última.
        List<ZoneReadingEntity> latest = readings.latest(zoneId, required + 1, true);
        if (!ev.compararFijo("Lecturas de pulso disponibles", latest.size(), Operador.GE, required)) {
            return List.of(RuleAction.noopInfo(NAME, String.format(
                    "Sin dato: hay %d lectura(s) de pulso de %s y se necesitan %d.", latest.size(), zoneId, required)));
        }

        List<ZoneReadingEntity> current = latest.subList(latest.size() - required, latest.size());
        if (!conditionHolds(current, ev)) {
            return List.of(RuleAction.noopInfo(NAME, "Las condiciones de " + zoneId + " no favorecen al hongo."));
        }

        boolean heldBefore = latest.size() > required
                && conditionHeldBefore(latest.subList(latest.size() - required - 1, latest.size() - 1), ev);
        if (ev.compararFijo("La condición ya se cumplía antes de la última lectura", heldBefore, Operador.EQ, true)) {
            return List.of(RuleAction.noopInfo(NAME, "Las condiciones favorables al hongo en " + zoneId
                    + " ya estaban en la lectura anterior: el evento ya se avisó."));
        }
        return alert(zoneId, latest.get(latest.size() - 1));
    }

    private static List<RuleAction> alert(String zoneId, ZoneReadingEntity latest) {
        String text = String.format("Condiciones favorables a enfermedades fúngicas en %s (DPV %.2f kPa, %.0f °C, sustrato %.0f %%).",
                zoneId, latest.getDpv(), latest.getTemp(), latest.getHumSus());
        return List.of(new RuleAction(ActionType.ALERTA, NAME, text, new DetalleAlerta(NivelAlerta.WARNING, text)));
    }

    /** Las tres condiciones con las lecturas dadas, registrando cada comparación en la traza. */
    private boolean conditionHolds(List<ZoneReadingEntity> window, Evaluacion ev) {
        ZoneReadingEntity last = window.get(window.size() - 1);
        boolean saturated = true;
        for (ZoneReadingEntity r : window) {
            saturated &= ev.comparar("DPV", ReadingMetric.DPV.valueIn(r), Operador.LT, ParametrosBiotico.DPV_SATURATED);
        }
        return saturated
                && ev.comparar("Temperatura del aire", ReadingMetric.TEMP.valueIn(last), Operador.GE,
                ParametrosBiotico.PREDISPOSING_TEMP_MIN)
                && ev.comparar("Temperatura del aire", ReadingMetric.TEMP.valueIn(last), Operador.LE,
                ParametrosBiotico.PREDISPOSING_TEMP_MAX)
                && ev.comparar("Humedad de sustrato", ReadingMetric.HUM_SUS.valueIn(last), Operador.GE,
                ParametrosBiotico.PREDISPOSING_MOISTURE);
    }

    /** Lo mismo sin dejar rastro en la traza: es una pregunta sobre el pasado, no una comparación de este pulso. */
    private boolean conditionHeldBefore(List<ZoneReadingEntity> window, Evaluacion ev) {
        ZoneReadingEntity last = window.get(window.size() - 1);
        double dpv = ev.numero(ParametrosBiotico.DPV_SATURATED);
        boolean saturated = window.stream().allMatch(r -> below(ReadingMetric.DPV.valueIn(r), dpv));
        Double temp = ReadingMetric.TEMP.valueIn(last);
        Double moisture = ReadingMetric.HUM_SUS.valueIn(last);
        return saturated
                && temp != null && temp >= ev.numero(ParametrosBiotico.PREDISPOSING_TEMP_MIN)
                && temp <= ev.numero(ParametrosBiotico.PREDISPOSING_TEMP_MAX)
                && moisture != null && moisture >= ev.numero(ParametrosBiotico.PREDISPOSING_MOISTURE);
    }

    private static boolean below(Double value, double limit) {
        return value != null && value < limit;
    }
}
