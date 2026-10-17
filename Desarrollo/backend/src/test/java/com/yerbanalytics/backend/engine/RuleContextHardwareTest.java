package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RuleContext - hardware mapeado (S-04)")
class RuleContextHardwareTest {

    private static RuleContext con(HardwareCoverage hardware) {
        return new RuleContext(RuleContextTestFactory.sectorBasico("MZ-1-001"), RuleContextTestFactory.zonaBasica("MZ-1"),
                List.of(), List.of(), RuleContextTestFactory.defaultConfig(), "ok", Instant.now(), null, false,
                ContextoRiego.vacio(), RuleOverrides.NONE, Set.of(), hardware);
    }

    @Test
    @DisplayName("sin cobertura (null) el contexto la lee como completa")
    void nullEsCompleta() {
        assertThat(con(null).hardware()).isEqualTo(HardwareCoverage.COMPLETE);
    }

    @Test
    @DisplayName("la cobertura dada se conserva, también al cambiar los overrides")
    void seConserva() {
        RuleContext ctx = con(HardwareCoverage.NONE);

        assertThat(ctx.hardware()).isEqualTo(HardwareCoverage.NONE);
        assertThat(ctx.withOverrides(RuleOverrides.NONE).hardware()).isEqualTo(HardwareCoverage.NONE);
    }

    @Test
    @DisplayName("los constructores anteriores arman un contexto con hardware completo y sin métricas descartadas")
    void constructoresAnteriores() {
        RuleContext ctx = new RuleContext(RuleContextTestFactory.sectorBasico("S"), RuleContextTestFactory.zonaBasica("Z"),
                List.of(), List.of(), RuleContextTestFactory.defaultConfig(), "ok", Instant.now(), null, false);

        assertThat(ctx.hardware()).isEqualTo(HardwareCoverage.COMPLETE);
        assertThat(ctx.invalidMetrics()).isEmpty();
    }
}
