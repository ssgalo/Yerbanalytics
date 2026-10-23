package com.yerbanalytics.backend.engine.zone;

import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.HardwareFaults;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ZoneRuleContext · qué sectores pueden dosificarse")
class ZoneRuleContextTest {

    private static SectorEntity sector(String id) {
        SectorEntity s = new SectorEntity();
        s.setId(id);
        return s;
    }

    private static ZoneRuleContext ctx(boolean zoneLocked, Set<String> locked, Map<String, HardwareCoverage> hw,
                                       Map<String, HardwareFaults> faults, SectorEntity... sectors) {
        return new ZoneRuleContext(new ZonaEntity(), List.of(sectors), Instant.EPOCH, hw, faults, zoneLocked, locked,
                RuleOverrides.NONE);
    }

    private static final HardwareCoverage WITH_PUMP = new HardwareCoverage(true, true, true, true);
    private static final HardwareCoverage WITHOUT_PUMP = new HardwareCoverage(true, false, true, true);

    @Test
    void unSectorConBombaYSinFallasNiBloqueosPuedeDosificarse() {
        SectorEntity a = sector("A");

        ZoneRuleContext ctx = ctx(false, Set.of(), Map.of("A", WITH_PUMP), Map.of(), a);

        assertThat(ctx.dosingBlock(a)).isEmpty();
        assertThat(ctx.dosableSectors()).containsExactly(a);
    }

    @Test
    void sinBombaRegistradaNoPuedeDosificarse() {
        SectorEntity a = sector("A");

        ZoneRuleContext ctx = ctx(false, Set.of(), Map.of("A", WITHOUT_PUMP), Map.of(), a);

        assertThat(ctx.dosingBlock(a)).hasValueSatisfying(r -> assertThat(r).contains("bomba"));
    }

    @Test
    void unSectorQueNoFiguraEnElHardwareNoTieneBomba() {
        SectorEntity a = sector("A");

        assertThat(ctx(false, Set.of(), Map.of(), Map.of(), a).dosingBlock(a)).isPresent();
    }

    @Test
    void conFallaHidraulicaNoPuedeDosificarse() {
        SectorEntity a = sector("A");

        ZoneRuleContext ctx = ctx(false, Set.of(), Map.of("A", WITH_PUMP),
                Map.of("A", new HardwareFaults("SIN_CAUDAL", null)), a);

        assertThat(ctx.dosingBlock(a)).hasValueSatisfying(r -> assertThat(r).contains("falla hidráulica"));
    }

    @Test
    void laFallaDeLaMallaNoImpideDosificar() {
        SectorEntity a = sector("A");

        ZoneRuleContext ctx = ctx(false, Set.of(), Map.of("A", WITH_PUMP),
                Map.of("A", new HardwareFaults(null, "MEDIASOMBRA_FIN_DE_CARRERA")), a);

        assertThat(ctx.dosingBlock(a)).isEmpty();
    }

    @Test
    void unBloqueoDelSectorLoExcluyeYLosDemasSiguen() {
        SectorEntity a = sector("A");
        SectorEntity b = sector("B");

        ZoneRuleContext ctx = ctx(false, Set.of("A"), Map.of("A", WITH_PUMP, "B", WITH_PUMP), Map.of(), a, b);

        assertThat(ctx.dosingBlock(a)).hasValueSatisfying(r -> assertThat(r).contains("bloqueo"));
        assertThat(ctx.dosableSectors()).containsExactly(b);
    }

    @Test
    void unBloqueoDeLaZonaExcluyeATodos() {
        SectorEntity a = sector("A");
        SectorEntity b = sector("B");

        ZoneRuleContext ctx = ctx(true, Set.of(), Map.of("A", WITH_PUMP, "B", WITH_PUMP), Map.of(), a, b);

        assertThat(ctx.dosableSectors()).isEmpty();
    }

    @Test
    void sinOverridesRigenLosValoresConfigurados() {
        ZoneRuleContext ctx = new ZoneRuleContext(new ZonaEntity(), null, Instant.EPOCH, null, null, false, null, null);

        assertThat(ctx.cooldownOverrideMinutes("R", "k")).isEmpty();
        assertThat(ctx.isBypassed("R", "k")).isFalse();
        assertThat(ctx.sectors()).isEmpty();
    }
}
