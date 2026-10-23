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
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * N-01 · Aplicación del plan de nutrición (reglas_v2 §8.4): cuando en la macro-zona pasaron los días del intervalo desde la
 * última aplicación del plan, pide fertilizante en cada sector de la zona, sin mirar lo que se mide. La celda tiene poco
 * volumen de sustrato y se lava con cada riego: sin aporte regular la clorosis aparece sola.
 *
 * <p><b>Qué decide y qué no.</b> Decide <i>cuándo</i> arranca la tanda. <i>Cuándo se aplica</i> cada sector lo resuelve
 * el {@code ApplicationService} con las precondiciones P-D2…P-D5 (ventana, temperatura, lluvia, humedad…): un sector
 * que no las cumple queda pendiente y se reintenta en la ventana siguiente, sin que esta regla intervenga.
 *
 * <p><b>Una tanda a la vez.</b> Con la tanda abierta no pide nada: espera a que no quede ningún sector esperando y recién
 * entonces la cierra ({@link DetalleNutritionPlan.Change#COMPLETE}), que es lo que reinicia el intervalo.
 *
 * <p><b>Bloqueo manual.</b> Con la zona bloqueada (S-01) el plan queda vencido y espera: no se descarta la tanda por una
 * causa que el operario puede levantar. Un bloqueo de un sector, una falla hidráulica o un sector sin bomba sólo dejan a
 * ese sector fuera de la tanda.
 *
 * <p><b>Alerta:</b> {@code INFO} al iniciar la tanda.
 *
 * <p><b>Simulador:</b> el cooldown {@code interval} (minutos) reemplaza el intervalo de la zona; {@code 0} lo vence ya.
 */
@Component
public class NutritionPlanRule implements ZoneRule {

    /** Prioridad: después de N-02, que puede adelantar el plan en el mismo pulso (única entre sectores y zonas). */
    private static final int PRIORITY = 23;
    public static final String NAME = "NutritionPlanRule";
    /** Cooldown key: minutes between plan applications. */
    public static final String COOLDOWN_INTERVAL = "interval";

    private static final double MINUTES_PER_DAY = 24.0 * 60;

    private final NutritionPlanService plan;

    public NutritionPlanRule(NutritionPlanService plan) {
        this.plan = plan;
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
        return "🌱 Plan de nutrición (N-01)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.INSUMO;
    }

    @Override
    public Set<String> cooldownKeys() {
        return Set.of(COOLDOWN_INTERVAL);
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosNutricion.PLAN_INTERVAL);
    }

    @Override
    public List<RuleAction> evaluate(ZoneRuleContext ctx, Evaluacion ev) {
        NutritionPlanFacts facts = plan.facts(ctx.zone());
        String zoneId = ctx.zone().getId();

        if (facts.batchOpen()) {
            if (ev.compararFijo("Aplicaciones del plan que esperan su ventana", facts.pendingInBatch(), Operador.EQ, 0)) {
                return List.of(new RuleAction(ActionType.UPDATE_NUTRITION_PLAN, NAME,
                        "Todos los sectores de " + zoneId + " ya recibieron o descartaron su aplicación: tanda completa.",
                        new DetalleNutritionPlan(DetalleNutritionPlan.Change.COMPLETE)));
            }
            return List.of(RuleAction.noopInfo(NAME, String.format(
                    "Tanda en curso: %d sector(es) de %s esperan su ventana de aplicación.", facts.pendingInBatch(), zoneId)));
        }

        double intervalDays = ctx.cooldownOverrideMinutes(NAME, COOLDOWN_INTERVAL)
                .map(minutes -> minutes / MINUTES_PER_DAY)
                .orElse((double) facts.intervalDays());
        Instant now = ctx.now();
        Double elapsedDays = facts.lastPlan() == null ? null
                : Duration.between(facts.lastPlan(), now).toMillis() / 60_000.0 / MINUTES_PER_DAY;

        // Vence si pasó el intervalo, si N-02 lo adelantó o si la zona nunca recibió el plan (D-5).
        boolean intervalReached = ev.compararFijo("Días desde la última aplicación del plan", elapsedDays, Operador.GE,
                intervalDays);
        boolean advanced = ev.compararFijo("Plan adelantado por CE baja", facts.advancedTo() != null && !now.isBefore(facts.advancedTo()),
                Operador.EQ, true);
        boolean neverApplied = facts.lastPlan() == null;
        if (!(intervalReached || advanced || neverApplied)) {
            return List.of(RuleAction.noopInfo(NAME, String.format(
                    "Todavía no: pasaron %.1f de los %.0f días del intervalo del plan de %s.",
                    elapsedDays, intervalDays, zoneId)));
        }

        if (!ev.compararFijo("Macro-zona sin bloqueo manual", !ctx.zoneLocked(), Operador.EQ, true)) {
            return List.of(RuleAction.noopInfo(NAME, "El plan de " + zoneId
                    + " está vencido pero la macro-zona tiene un bloqueo manual: espera a que se reanude."));
        }
        int dosable = ctx.dosableSectors().size();
        if (!ev.compararFijo("Sectores que pueden recibir fertilizante", dosable, Operador.GT, 0)) {
            return List.of(RuleAction.noopInfo(NAME, "El plan de " + zoneId
                    + " está vencido pero ningún sector puede dosificarse (sin bomba registrada, con falla hidráulica o bloqueado)."));
        }

        int excluded = ctx.sectors().size() - dosable;
        String reason = neverApplied
                ? "La macro-zona " + zoneId + " nunca recibió el plan de nutrición."
                : advanced ? "El plan de " + zoneId + " fue adelantado por CE baja."
                : String.format("Pasaron %.1f días desde la última aplicación del plan de %s.", elapsedDays, zoneId);
        String alert = String.format("Plan de nutrición de %s: se pidió fertilizante para %d sector(es)%s; cada uno se aplica "
                        + "en su próxima ventana apta.", zoneId, dosable,
                excluded > 0 ? " (" + excluded + " quedan fuera de esta tanda)" : "");
        return List.of(
                new RuleAction(ActionType.UPDATE_NUTRITION_PLAN, NAME, reason,
                        new DetalleNutritionPlan(DetalleNutritionPlan.Change.START)),
                new RuleAction(ActionType.ALERTA, NAME, alert, new DetalleAlerta(NivelAlerta.INFO, alert)));
    }
}
