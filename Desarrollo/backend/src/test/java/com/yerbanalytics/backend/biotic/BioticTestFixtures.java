package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.ContextoRiego;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.HardwareFaults;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.overrides.TestRuleOverrides;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.model.ZoneReadingEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Armado de zonas, sectores con diagnóstico, lecturas y contextos para los tests de las reglas bióticas. */
final class BioticTestFixtures {

    static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private BioticTestFixtures() {
    }

    static ZonaEntity zone(String id) {
        ZonaEntity z = new ZonaEntity();
        z.setId(id);
        z.setName("Macro-zona " + id);
        return z;
    }

    /** Un sector cuyo último diagnóstico llegó hace {@code hoursAgo} horas. */
    static SectorEntity sector(ZonaEntity zone, int n, String estado, String severity, Double conf, long hoursAgo) {
        SectorEntity s = RuleContextTestFactory.sectorBasico(String.format("%s-%03d", zone.getId(), n));
        s.setZona(zone);
        s.setN(n);
        s.setDiagnosisEstado(estado);
        s.setDiagnosisSev(severity);
        s.setDiagnosisConf(conf);
        s.setDiagnosisTs(NOW.minusSeconds(hoursAgo * 3600).toEpochMilli());
        return s;
    }

    static SectorEntity healthy(ZonaEntity zone, int n) {
        return sector(zone, n, "Sano", "—", 95.0, 1);
    }

    static RuleContext ctx(SectorEntity sector) {
        return ctx(sector, RuleOverrides.NONE);
    }

    static RuleContext ctx(SectorEntity sector, RuleOverrides overrides) {
        return new RuleContext(sector, sector.getZona(), List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", NOW, null, false, ContextoRiego.vacio(), overrides, Set.of(), HardwareCoverage.COMPLETE,
                HardwareFaults.NONE);
    }

    /** Overrides con las claves de bypass activas en la regla. */
    static RuleOverrides bypass(String ruleName, String... keys) {
        TestRuleOverrides store = new TestRuleOverrides();
        store.put(ruleName, new RuleOverride(Map.of(), Set.of(keys)));
        return store;
    }

    /** Overrides con un cooldown (en minutos) de la regla. */
    static RuleOverrides cooldown(String ruleName, String key, double minutes) {
        TestRuleOverrides store = new TestRuleOverrides();
        store.put(ruleName, new RuleOverride(Map.of(key, minutes), Set.of()));
        return store;
    }

    /** Una lectura de pulso de la zona, de hace {@code hoursAgo} horas. */
    static ZoneReadingEntity reading(double hoursAgo, Double humSus, Double dpv, Double temp) {
        ZoneReadingEntity r = new ZoneReadingEntity();
        r.setZoneId("MZ-1");
        r.setTs(NOW.minusSeconds(Math.round(hoursAgo * 3600)).toEpochMilli());
        r.setHumSus(humSus);
        r.setDpv(dpv);
        r.setTemp(temp);
        return r;
    }

    static ZoneReadingEntity reading(double hoursAgo, Double humSus, Double dpv) {
        return reading(hoursAgo, humSus, dpv, null);
    }

    /** Una captura diagnosticada {@code daysAgo} días antes de {@link #NOW}. */
    static ConsecutiveCaptures.Capture capture(String estado, double conf, int daysAgo) {
        return new ConsecutiveCaptures.Capture(estado, conf, NOW.minusSeconds(daysAgo * 86_400L).toEpochMilli());
    }

    static boolean has(List<RuleAction> actions, ActionType type) {
        return actions.stream().anyMatch(a -> a.type() == type);
    }

    static RuleAction first(List<RuleAction> actions, ActionType type) {
        return actions.stream().filter(a -> a.type() == type).findFirst().orElseThrow();
    }
}
