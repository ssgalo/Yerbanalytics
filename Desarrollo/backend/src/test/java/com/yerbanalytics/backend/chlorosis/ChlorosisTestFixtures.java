package com.yerbanalytics.backend.chlorosis;

import com.yerbanalytics.backend.dto.Metric;
import com.yerbanalytics.backend.engine.ContextoRiego;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.HardwareFaults;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Armado de sectores con diagnóstico y de contextos con lecturas para los tests de las reglas de clorosis. */
final class ChlorosisTestFixtures {

    static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private ChlorosisTestFixtures() {
    }

    /** Un sector de MZ-1 con un diagnóstico de hace 2 h. */
    static SectorEntity sector(String estado, String severity, Double conf) {
        SectorEntity s = RuleContextTestFactory.sectorBasico("MZ-1-045");
        ZonaEntity z = RuleContextTestFactory.zonaBasica("MZ-1");
        s.setZona(z);
        s.setDiagnosisEstado(estado);
        s.setDiagnosisSev(severity);
        s.setDiagnosisConf(conf);
        s.setDiagnosisTs(NOW.minusSeconds(2 * 3600).toEpochMilli());
        return s;
    }

    /** Una métrica de la lectura de la zona tal como llega al contexto de las reglas. */
    static Metric metric(String key, Double raw) {
        return new Metric(key, key, "", raw, String.valueOf(raw), "ok", "#000", null);
    }

    static RuleContext ctx(SectorEntity sector, Metric... metrics) {
        return ctx(sector, RuleOverrides.NONE, Set.of(), metrics);
    }

    static RuleContext ctx(SectorEntity sector, RuleOverrides overrides, Set<String> invalidMetrics, Metric... metrics) {
        return new RuleContext(sector, sector.getZona(), List.of(), new ArrayList<>(List.of(metrics)),
                RuleContextTestFactory.defaultConfig(), "ok", NOW, null, false, ContextoRiego.vacio(), overrides,
                invalidMetrics, HardwareCoverage.COMPLETE, HardwareFaults.NONE);
    }

    static boolean has(List<RuleAction> actions, ActionType type) {
        return actions.stream().anyMatch(a -> a.type() == type);
    }

    static RuleAction first(List<RuleAction> actions, ActionType type) {
        return actions.stream().filter(a -> a.type() == type).findFirst().orElseThrow();
    }
}
