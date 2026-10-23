package com.yerbanalytics.backend.solarstress;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.dto.Metric;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Armado de sectores con diagnóstico y de contextos con hora local para los tests del estrés solar. */
final class SolarStressTestFixtures {

    static final LocalDate DAY = LocalDate.of(2026, 10, 22);

    private SolarStressTestFixtures() {
    }

    /** El instante UTC de una hora local del vivero del {@link #DAY}. */
    static Instant at(String localTime) {
        return ZonedDateTime.of(DAY, LocalTime.parse(localTime), ZonaHorariaVivero.ZONA).toInstant();
    }

    /** Un sector de MZ-1 con un diagnóstico de hace 2 h respecto de {@code now}. */
    static SectorEntity sector(String estado, String severity, Double conf, Instant now) {
        SectorEntity s = RuleContextTestFactory.sectorBasico("MZ-1-045");
        ZonaEntity z = RuleContextTestFactory.zonaBasica("MZ-1");
        s.setZona(z);
        s.setDiagnosisEstado(estado);
        s.setDiagnosisSev(severity);
        s.setDiagnosisConf(conf);
        s.setDiagnosisTs(now.minusSeconds(2 * 3600).toEpochMilli());
        return s;
    }

    static Metric metric(String key, Double raw) {
        return new Metric(key, key, "", raw, String.valueOf(raw), "ok", "#000", null);
    }

    static RuleContext ctx(SectorEntity sector, Instant now, ContextoRiego riego, RuleOverrides overrides, Metric... metrics) {
        return new RuleContext(sector, sector.getZona(), List.of(), List.of(metrics), RuleContextTestFactory.defaultConfig(),
                "ok", now, null, false, riego, overrides, Set.of(), HardwareCoverage.COMPLETE, HardwareFaults.NONE);
    }

    static RuleContext ctx(SectorEntity sector, Instant now, Metric... metrics) {
        return ctx(sector, now, ContextoRiego.vacio(), RuleOverrides.NONE, metrics);
    }

    static RuleOverrides bypass(String ruleName, String... keys) {
        TestRuleOverrides store = new TestRuleOverrides();
        store.put(ruleName, new RuleOverride(true, Map.of(), Set.of(keys)));
        return store;
    }

    static RuleOverrides cooldown(String ruleName, String key, double minutes) {
        TestRuleOverrides store = new TestRuleOverrides();
        store.put(ruleName, new RuleOverride(true, Map.of(key, minutes), Set.of()));
        return store;
    }

    static boolean has(List<RuleAction> actions, ActionType type) {
        return actions.stream().anyMatch(a -> a.type() == type);
    }

    static RuleAction first(List<RuleAction> actions, ActionType type) {
        return actions.stream().filter(a -> a.type() == type).findFirst().orElseThrow();
    }
}
