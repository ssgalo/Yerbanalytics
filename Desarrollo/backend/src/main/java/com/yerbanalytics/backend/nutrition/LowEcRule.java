package com.yerbanalytics.backend.nutrition;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleNutritionPlan;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosNutricion;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.zone.ZoneRule;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.readings.ReadingHistory;
import com.yerbanalytics.backend.readings.ReadingMetric;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * N-02 · CE baja (reglas_v2 §8.4): si la CE media de las últimas lecturas de la macro-zona es menor a
 * {@code nutricion.ce-baja} y no hubo fertilización en la zona en los últimos {@code nutricion.sin-fertilizar-dias},
 * adelanta N-01 a la próxima ventana apta. No fertiliza ella: marca el plan como vencido y N-01, que corre justo después,
 * pide la tanda.
 *
 * <p><b>Sin dato no decide.</b> Con menos lecturas de pulso que {@code nutricion.ce-lecturas} (6: un día entero con el pulso
 * de 4 h) la traza dice «sin dato» y la regla no actúa: una media de dos lecturas no es la CE de las últimas 24 h. «Sin
 * fertilizar» cuenta cualquier aplicación de fertilizante en la zona, del plan o correctiva.
 *
 * <p><b>Una alerta por evento.</b> Mientras el plan está adelantado o en curso no vuelve a alertar: la condición sigue
 * siendo cierta en el pulso siguiente, pero el evento ya se avisó (principio 8 de la v2). Cuando N-01 aplica, la
 * fertilización reciente corta la condición sola.
 *
 * <p><b>Alerta:</b> {@code INFO} «CE baja: la fertilización de base del sustrato podría estar agotada».
 *
 * <p><b>Simulador:</b> el cooldown {@code no-fertilized} (minutos) reemplaza los días sin fertilizar; {@code 0} lo elimina.
 * El bypass {@code samples} usa la última lectura de CE como media, sin exigir las del catálogo.
 */
@Component
public class LowEcRule implements ZoneRule {

    /** Prioridad: antes de N-01 (sanidad antes que rutina), para adelantarlo en el mismo pulso. */
    private static final int PRIORITY = 22;
    public static final String NAME = "LowEcRule";
    /** Cooldown key: minutes without fertilizing. */
    public static final String COOLDOWN_NO_FERTILIZED = "no-fertilized";
    /** Bypass key: uses the latest EC reading as the average. */
    public static final String BYPASS_SAMPLES = "samples";

    static final String ALERT_TEXT = "CE baja: la fertilización de base del sustrato podría estar agotada";

    private static final double MINUTES_PER_DAY = 24.0 * 60;

    private final NutritionPlanService plan;
    private final ReadingHistory history;

    public LowEcRule(NutritionPlanService plan, ReadingHistory history) {
        this.plan = plan;
        this.history = history;
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
        return "🧂 CE baja (N-02)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.INSUMO;
    }

    @Override
    public Set<String> cooldownKeys() {
        return Set.of(COOLDOWN_NO_FERTILIZED);
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_SAMPLES);
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosNutricion.LOW_EC, ParametrosNutricion.EC_SAMPLES, ParametrosNutricion.NO_FERTILIZED_DAYS);
    }

    @Override
    public List<RuleAction> evaluate(ZoneRuleContext ctx, Evaluacion ev) {
        NutritionPlanFacts facts = plan.facts(ctx.zone());
        String zoneId = ctx.zone().getId();

        if (!ev.compararFijo("Plan de nutrición sin adelantar ni en curso", !facts.pending(), Operador.EQ, true)) {
            return List.of(RuleAction.noopInfo(NAME, "El plan de " + zoneId + " ya está adelantado o en curso: no se vuelve a avisar."));
        }

        boolean lastReadingOnly = ctx.isBypassed(NAME, BYPASS_SAMPLES);
        int required = lastReadingOnly ? 1 : (int) Math.round(ev.numero(ParametrosNutricion.EC_SAMPLES));
        List<Double> ec = history.latest(zoneId, required, !lastReadingOnly).stream()
                .map(ReadingMetric.CE::valueIn).filter(v -> v != null).toList();
        if (!ev.compararFijo("Lecturas de CE disponibles", ec.size(), Operador.GE, required)) {
            return List.of(RuleAction.noopInfo(NAME, String.format(
                    "Sin dato: hay %d lectura(s) de CE de %s y se necesitan %d para la media.", ec.size(), zoneId, required)));
        }
        double average = ec.stream().mapToDouble(Double::doubleValue).average().orElseThrow();

        if (!ev.comparar("CE media de las últimas " + required + " lecturas", average, Operador.LT, ParametrosNutricion.LOW_EC)) {
            return List.of(RuleAction.noopInfo(NAME, String.format(
                    "CE media de %.2f dS/m en %s: no está por debajo del umbral.", average, zoneId)));
        }

        if (!withoutRecentFertilization(ctx, ev, facts.lastFertilization())) {
            return List.of(RuleAction.noopInfo(NAME, String.format(
                    "CE baja (%.2f dS/m) en %s, pero se fertilizó hace poco: todavía no se adelanta el plan.", average, zoneId)));
        }

        String text = ALERT_TEXT + String.format(" (%s: CE media %.2f dS/m).", zoneId, average);
        return List.of(
                new RuleAction(ActionType.UPDATE_NUTRITION_PLAN, NAME,
                        String.format("CE media de %.2f dS/m sin fertilizar hace poco: se adelanta el plan de %s.", average, zoneId),
                        new DetalleNutritionPlan(DetalleNutritionPlan.Change.ADVANCE)),
                new RuleAction(ActionType.ALERTA, NAME, text, new DetalleAlerta(NivelAlerta.INFO, text)));
    }

    /** «No hubo fertilización en la zona en los últimos N días»; una zona que nunca fertilizó lo cumple. */
    private boolean withoutRecentFertilization(ZoneRuleContext ctx, Evaluacion ev, Instant lastFertilization) {
        if (ev.compararFijo("Zona sin ninguna fertilización registrada", lastFertilization == null, Operador.EQ, true)) {
            return true;
        }
        double elapsedDays = Duration.between(lastFertilization, ctx.now()).toMillis() / 60_000.0 / MINUTES_PER_DAY;
        var overridden = ctx.cooldownOverrideMinutes(NAME, COOLDOWN_NO_FERTILIZED);
        if (overridden.isPresent()) {
            return ev.compararFijo("Días desde la última fertilización de la zona", elapsedDays, Operador.GE,
                    overridden.get() / MINUTES_PER_DAY);
        }
        return ev.comparar("Días desde la última fertilización de la zona", elapsedDays, Operador.GE,
                ParametrosNutricion.NO_FERTILIZED_DAYS);
    }
}
