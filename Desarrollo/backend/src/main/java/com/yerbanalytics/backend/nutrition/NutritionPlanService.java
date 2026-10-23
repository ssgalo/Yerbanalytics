package com.yerbanalytics.backend.nutrition;

import com.yerbanalytics.backend.dto.NutritionPlan;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleNutritionPlan;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DeclaredParameters;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosNutricion;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.exception.InvalidNutritionIntervalException;
import com.yerbanalytics.backend.exception.NutritionZoneNotFoundException;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ApplicationRepository;
import com.yerbanalytics.backend.repository.PendingApplicationRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.HistorialService;
import com.yerbanalytics.backend.supply.ApplicationOutcome;
import com.yerbanalytics.backend.supply.ApplicationService;
import com.yerbanalytics.backend.supply.Supply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * El estado del plan de nutrición de cada macro-zona y las transiciones que le piden las reglas N-01 y N-02
 * (reglas_v2 §8.4). Las reglas DECIDEN (iniciar, adelantar, cerrar); este servicio EJECUTA, y cada aplicación pasa por el
 * {@link ApplicationService}, el único camino para aplicar un insumo.
 *
 * <p><b>La tanda.</b> Iniciar el plan pide fertilizante en cada sector de la zona y abre una <i>tanda</i>. La tanda está
 * abierta mientras algún sector espera su ventana (queda en {@code pending_application}) y se cierra cuando ninguno espera:
 * todos se aplicaron o se descartaron con motivo. Recién ahí corre el intervalo: {@code last_plan_application_ts} es el
 * momento en que se aplicó el último sector, no el de la decisión, para que un plan que tardó un día en completarse no
 * acorte el siguiente. Mientras la tanda está abierta N-01 no vuelve a pedir nada (los pedidos son idempotentes por
 * sector e insumo, pero así tampoco se repiten los avisos).
 *
 * <p>Corre en la transacción de quien llama (la ingesta de telemetría): el estado de la zona, las aplicaciones y la cola
 * se confirman o se revierten juntos.
 */
@Service
public class NutritionPlanService implements ConsumidorParametros {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlanService.class);

    public static final String NAME = "NutritionPlanService";

    private final ApplicationService applicationService;
    private final ApplicationRepository applications;
    private final PendingApplicationRepository pending;
    private final ZonaRepository zones;
    private final HistorialService history;
    private final CatalogoParametrosService catalog;
    private final DeclaredParameters params;

    public NutritionPlanService(ApplicationService applicationService,
                                ApplicationRepository applications,
                                PendingApplicationRepository pending,
                                ZonaRepository zones,
                                HistorialService history,
                                // Perezoso: el catálogo necesita a este bean (es un consumidor de parámetros) para armarse.
                                @Lazy CatalogoParametrosService catalog) {
        this.applicationService = applicationService;
        this.applications = applications;
        this.pending = pending;
        this.zones = zones;
        this.history = history;
        this.catalog = catalog;
        this.params = new DeclaredParameters(this, catalog::vigentes);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosNutricion.PLAN_INTERVAL);
    }

    // -------------------------------------------------------------------------
    // Lectura
    // -------------------------------------------------------------------------

    /** Los días entre aplicaciones del plan de la zona: el suyo, o el del catálogo si todavía no fijó uno. */
    public int intervalDays(ZonaEntity zone) {
        Integer own = zone.getNutritionPlanIntervalDays();
        return own != null ? own : (int) Math.round(params.number(ParametrosNutricion.PLAN_INTERVAL));
    }

    /** El estado del plan que ven las reglas. */
    public NutritionPlanFacts facts(ZonaEntity zone) {
        String zoneId = zone.getId();
        Instant batchStart = instant(zone.getNutritionBatchStartTs());
        long waiting = batchStart == null ? 0
                : pending.countByZoneIdAndSupplyAndRuleName(zoneId, Supply.FERTILIZER, NutritionPlanRule.NAME);
        return new NutritionPlanFacts(
                intervalDays(zone),
                instant(zone.getLastPlanApplicationTs()),
                instant(zone.getNutritionPlanDueTs()),
                batchStart,
                waiting,
                applications.findFirstByZoneIdAndSupplyOrderByTsDesc(zoneId, Supply.FERTILIZER)
                        .map(a -> Instant.ofEpochMilli(a.getTs())).orElse(null));
    }

    /** El plan de la zona para el snapshot del dashboard. */
    public NutritionPlan status(ZonaEntity zone) {
        int days = intervalDays(zone);
        Instant last = instant(zone.getLastPlanApplicationTs());
        boolean batchOpen = zone.getNutritionBatchStartTs() != null;
        Long nextDue = batchOpen ? null
                : NutritionSchedule.nextDue(last, instant(zone.getNutritionPlanDueTs()), Duration.ofDays(days))
                        .map(Instant::toEpochMilli).orElse(null);
        return new NutritionPlan(days, zone.getNutritionPlanIntervalDays() != null, zone.getLastPlanApplicationTs(),
                nextDue, batchOpen, !batchOpen && zone.getNutritionPlanDueTs() != null);
    }

    // -------------------------------------------------------------------------
    // Intervalo de la zona
    // -------------------------------------------------------------------------

    /**
     * Fija los días entre aplicaciones del plan de la zona. El rango es el del parámetro del catálogo
     * ({@code nutricion.intervalo-plan}, 3–30): un valor fuera de rango se rechaza, no se recorta.
     *
     * @throws NutritionZoneNotFoundException    si la zona no existe
     * @throws InvalidNutritionIntervalException si el intervalo falta o está fuera de rango
     */
    @Transactional
    public NutritionPlan setInterval(String zoneId, Integer days, String user) {
        ZonaEntity zone = zones.findById(zoneId).orElseThrow(() -> new NutritionZoneNotFoundException(zoneId));
        DefinicionParametro definition = ParametrosNutricion.PLAN_INTERVAL;
        if (days == null) {
            throw new InvalidNutritionIntervalException("Falta el intervalo del plan de nutrición (intervalDays).");
        }
        if (days < definition.min() || days > definition.max()) {
            throw new InvalidNutritionIntervalException(String.format(
                    "El intervalo del plan de nutrición debe estar entre %.0f y %.0f días.", definition.min(), definition.max()));
        }
        int before = intervalDays(zone);
        zone.setNutritionPlanIntervalDays(days);
        zones.save(zone);
        if (before != days) {
            history.registrarConfiguracion(user, String.format("Intervalo del plan de nutrición de %s: %d → %d días.",
                    zoneId, before, days));
        }
        return status(zone);
    }

    // -------------------------------------------------------------------------
    // Transiciones
    // -------------------------------------------------------------------------

    /**
     * Ejecuta la transición que decidió una regla.
     *
     * @param ruleName la regla que la pidió: con ese nombre quedan registradas las aplicaciones de la tanda
     */
    public void apply(DetalleNutritionPlan.Change change, ZoneRuleContext ctx, String ruleName) {
        switch (change) {
            case START -> start(ctx, ruleName);
            case ADVANCE -> advance(ctx.zone(), ctx.now());
            case COMPLETE -> complete(ctx.zone(), ctx.now(), ruleName);
        }
    }

    /** Abre la tanda: un pedido de fertilizante por cada sector que puede dosificarse. */
    private void start(ZoneRuleContext ctx, String ruleName) {
        ZonaEntity zone = ctx.zone();
        if (zone.getNutritionBatchStartTs() != null) {
            return;   // ya hay una tanda abierta: los pedidos de este ciclo ya salieron
        }
        int applied = 0;
        int deferred = 0;
        int rejected = 0;
        for (SectorEntity sector : ctx.sectors()) {
            var block = ctx.dosingBlock(sector);
            if (block.isPresent()) {
                history.registrarInaccion(sector, ActionType.NOOP_INFO, ruleName, String.format(
                        "Plan de nutrición: este sector no recibe fertilizante en esta tanda (%s).", block.get()));
                continue;
            }
            ApplicationOutcome outcome = applicationService.request(sector, Supply.FERTILIZER, ruleName, false);
            switch (outcome.status()) {
                case APPLIED -> applied++;
                case DEFERRED -> deferred++;
                case REJECTED -> rejected++;
            }
        }
        zone.setNutritionBatchStartTs(ctx.now().toEpochMilli());
        zone.setNutritionPlanDueTs(null);
        zones.save(zone);
        log.info("Zona {}: plan de nutrición iniciado — {} aplicados, {} diferidos a la próxima ventana, {} rechazados.",
                zone.getId(), applied, deferred, rejected);
        complete(zone, ctx.now(), ruleName);   // si nadie quedó esperando, la tanda termina en este mismo ciclo
    }

    /** Vence el plan ahora. Si ya está adelantado o en curso no hay nada que adelantar. */
    private void advance(ZonaEntity zone, Instant now) {
        if (zone.getNutritionBatchStartTs() != null || zone.getNutritionPlanDueTs() != null) {
            return;
        }
        zone.setNutritionPlanDueTs(now.toEpochMilli());
        zones.save(zone);
    }

    /** Cierra la tanda si ya no queda ninguna aplicación del plan esperando; si no, la deja abierta. */
    private void complete(ZonaEntity zone, Instant now, String ruleName) {
        Long batchStart = zone.getNutritionBatchStartTs();
        if (batchStart == null) {
            return;
        }
        if (pending.countByZoneIdAndSupplyAndRuleName(zone.getId(), Supply.FERTILIZER, ruleName) > 0) {
            return;
        }
        // La tanda terminó cuando se aplicó el último sector; si ninguno se aplicó (todos rechazados), cuando se decidió.
        long finished = applications.findFirstByZoneIdAndSupplyAndRuleNameAndTsGreaterThanEqualOrderByTsDesc(
                        zone.getId(), Supply.FERTILIZER, ruleName, batchStart)
                .map(a -> a.getTs()).orElse(now.toEpochMilli());
        zone.setLastPlanApplicationTs(finished);
        zone.setNutritionBatchStartTs(null);
        zones.save(zone);
        log.info("Zona {}: tanda del plan de nutrición completa.", zone.getId());
    }

    private static Instant instant(Long epochMs) {
        return epochMs == null ? null : Instant.ofEpochMilli(epochMs);
    }
}
