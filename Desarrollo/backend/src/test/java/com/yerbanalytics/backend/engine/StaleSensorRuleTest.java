package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.rules.StaleSensorRule;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de {@link StaleSensorRule} (tarea 2.2).
 *
 * <p>No requiere Spring — contexto fabricado con {@link RuleContextTestFactory}.
 */
@DisplayName("StaleSensorRule")
class StaleSensorRuleTest {

    private StaleSensorRule rule;
    private SectorEntity sector;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        rule = new StaleSensorRule();
        sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        zona = RuleContextTestFactory.zonaBasica("MZ-1");
    }

    @Test
    @DisplayName("sensor stale → emite ABORT_RIEGO")
    void cuandoSensorStale_emiteAbortRiego() {
        RuleContext ctx = RuleContextTestFactory.conSensorStale(sector, zona, true);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.ABORT_RIEGO);
        assertThat(acciones.get(0).ruleName()).isEqualTo("StaleSensorRule");
        assertThat(acciones.get(0).motivo()).contains("MZ-1");
    }

    @Test
    @DisplayName("sensor fresco → emite NOOP_INFO")
    void cuandoSensorFresco_emiteNoopInfo() {
        RuleContext ctx = RuleContextTestFactory.conSensorStale(sector, zona, false);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ABORT_RIEGO es una acción bloqueante")
    void abortRiegoEsBloqueante() {
        assertThat(ActionType.ABORT_RIEGO.isBlocking()).isTrue();
    }
}
