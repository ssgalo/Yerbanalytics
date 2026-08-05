package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.rules.ManualLockRule;
import com.yerbanalytics.backend.model.BloqueoManualEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de {@link ManualLockRule} (tarea 4.7).
 */
@DisplayName("ManualLockRule")
@ExtendWith(MockitoExtension.class)
class ManualLockRuleTest {

    @Mock
    private ManualLockRepository manualLockRepository;

    private ManualLockRule rule;
    private SectorEntity sector;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        rule = new ManualLockRule(manualLockRepository);
        sector = RuleContextTestFactory.sectorBasico("MZ-3-005");
        zona = RuleContextTestFactory.zonaBasica("MZ-3");
    }

    @Test
    @DisplayName("sin bloqueo activo → NOOP_INFO")
    void sinBloqueo_emiteNoopInfo() {
        RuleContext ctx = RuleContextTestFactory.conBloqueo(sector, zona, false);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("con bloqueo activo → ABORT_ALL")
    void conBloqueo_emiteAbortAll() {
        RuleContext ctx = RuleContextTestFactory.conBloqueo(sector, zona, true);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.ABORT_ALL);
        assertThat(acciones.get(0).motivo()).contains("MZ-3-005");
    }

    @Test
    @DisplayName("ABORT_ALL es una acción bloqueante")
    void abortAllEsBloqueante() {
        assertThat(ActionType.ABORT_ALL.isBlocking()).isTrue();
    }

    @Test
    @DisplayName("prioridad es 0 (la más alta)")
    void prioridad_esLaMasAlta() {
        assertThat(rule.priority()).isEqualTo(0);
    }
}
