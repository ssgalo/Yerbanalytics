package com.yerbanalytics.backend.nutrition;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** Armado de zonas, contextos y estados del plan para los tests de nutrición. */
final class NutritionTestFixtures {

    static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    static final HardwareCoverage WITH_PUMP = new HardwareCoverage(true, true, true, true);

    private NutritionTestFixtures() {
    }

    static ZonaEntity zone(String id) {
        ZonaEntity z = new ZonaEntity();
        z.setId(id);
        z.setName("Macro-zona " + id);
        return z;
    }

    static SectorEntity sector(ZonaEntity zone, int n) {
        SectorEntity s = new SectorEntity();
        s.setId(String.format("%s-%03d", zone.getId(), n));
        s.setZona(zone);
        return s;
    }

    static List<SectorEntity> sectors(ZonaEntity zone, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(n -> sector(zone, n)).toList();
    }

    /** Todos los sectores con bomba, sin fallas ni bloqueos y sin overrides. */
    static ZoneRuleContext ctx(ZonaEntity zone, List<SectorEntity> sectors) {
        Map<String, HardwareCoverage> hardware = new HashMap<>();
        sectors.forEach(s -> hardware.put(s.getId(), WITH_PUMP));
        return new ZoneRuleContext(zone, sectors, NOW, hardware, Map.of(), false, Set.of(), RuleOverrides.NONE);
    }

    static ZoneRuleContext ctx(ZonaEntity zone, List<SectorEntity> sectors, RuleOverrides overrides) {
        return ctx(zone, sectors).withOverrides(overrides);
    }

    /** El plan sin tanda ni adelantamiento; su última aplicación fue hace {@code daysSinceLastPlan} días (o nunca). */
    static NutritionPlanFacts idle(int interval, Integer daysSinceLastPlan) {
        return new NutritionPlanFacts(interval,
                daysSinceLastPlan == null ? null : NOW.minusSeconds(daysSinceLastPlan * 86_400L), null, null, 0, null);
    }

    static NutritionPlanFacts withLastFertilization(NutritionPlanFacts f, Integer daysAgo) {
        return new NutritionPlanFacts(f.intervalDays(), f.lastPlan(), f.advancedTo(), f.batchStart(), f.pendingInBatch(),
                daysAgo == null ? null : NOW.minusSeconds(daysAgo * 86_400L));
    }

    static NutritionPlanFacts advanced(NutritionPlanFacts f) {
        return new NutritionPlanFacts(f.intervalDays(), f.lastPlan(), NOW, null, 0, f.lastFertilization());
    }

    static NutritionPlanFacts batchOpen(NutritionPlanFacts f, long pending) {
        return new NutritionPlanFacts(f.intervalDays(), f.lastPlan(), null, NOW.minusSeconds(3600), pending,
                f.lastFertilization());
    }

    static boolean has(List<RuleAction> actions, ActionType type) {
        return actions.stream().anyMatch(a -> a.type() == type);
    }
}
