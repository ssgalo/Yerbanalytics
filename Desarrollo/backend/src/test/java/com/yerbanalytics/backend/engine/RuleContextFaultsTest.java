package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RuleContext - fallas de hardware (S-05)")
class RuleContextFaultsTest {

    private static RuleContext con(HardwareFaults faults) {
        return new RuleContext(RuleContextTestFactory.sectorBasico("MZ-1-001"), RuleContextTestFactory.zonaBasica("MZ-1"),
                List.of(), List.of(), RuleContextTestFactory.defaultConfig(), "ok", Instant.now(), null, false,
                ContextoRiego.vacio(), RuleOverrides.NONE, Set.of(), HardwareCoverage.COMPLETE, faults);
    }

    @Test
    @DisplayName("sin fallas (null) el contexto las lee como ninguna")
    void nullEsNinguna() {
        assertThat(con(null).faults()).isEqualTo(HardwareFaults.NONE);
    }

    @Test
    @DisplayName("las fallas dadas se conservan, también al cambiar los overrides")
    void seConservan() {
        HardwareFaults faults = new HardwareFaults("SIN_CAUDAL", "MEDIASOMBRA_FIN_DE_CARRERA");

        assertThat(con(faults).faults()).isEqualTo(faults);
        assertThat(con(faults).withOverrides(RuleOverrides.NONE).faults()).isEqualTo(faults);
    }

    @Test
    @DisplayName("los constructores anteriores arman un contexto sin fallas")
    void constructoresAnteriores() {
        RuleContext ctx = new RuleContext(RuleContextTestFactory.sectorBasico("S"), RuleContextTestFactory.zonaBasica("Z"),
                List.of(), List.of(), RuleContextTestFactory.defaultConfig(), "ok", Instant.now(), null, false,
                ContextoRiego.vacio(), RuleOverrides.NONE, Set.of(), HardwareCoverage.NONE);

        assertThat(ctx.faults()).isEqualTo(HardwareFaults.NONE);
        assertThat(RuleContextTestFactory.basico(RuleContextTestFactory.sectorBasico("S"),
                RuleContextTestFactory.zonaBasica("Z")).faults()).isEqualTo(HardwareFaults.NONE);
    }

    @Test
    @DisplayName("HardwareFaults distingue la falla hidráulica del sector de la de la malla de la zona")
    void alcances() {
        assertThat(HardwareFaults.NONE.hasHydraulic()).isFalse();
        assertThat(HardwareFaults.NONE.hasShade()).isFalse();
        assertThat(new HardwareFaults("SIN_CAUDAL", null).hasHydraulic()).isTrue();
        assertThat(new HardwareFaults("SIN_CAUDAL", null).hasShade()).isFalse();
        assertThat(new HardwareFaults(null, "MEDIASOMBRA_FIN_DE_CARRERA").hasShade()).isTrue();
        assertThat(new HardwareFaults(" ", null).hasHydraulic()).as("una falla en blanco no es una falla").isFalse();
    }
}
