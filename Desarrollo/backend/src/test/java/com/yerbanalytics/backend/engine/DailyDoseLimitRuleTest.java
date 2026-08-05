package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.rules.DailyDoseLimitRule;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link DailyDoseLimitRule} (tarea 4.7).
 */
@DisplayName("DailyDoseLimitRule")
@ExtendWith(MockitoExtension.class)
class DailyDoseLimitRuleTest {

    @Mock
    private HistorialRepository historialRepository;

    private DailyDoseLimitRule rule;
    private SectorEntity sector;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        rule = new DailyDoseLimitRule(historialRepository);
        sector = RuleContextTestFactory.sectorBasico("MZ-1-010");
        zona = RuleContextTestFactory.zonaBasica("MZ-1");
    }

    @Test
    @DisplayName("sin dosis en 24h → NOOP_INFO")
    void sinDosisEn24h_emiteNoopInfo() {
        when(historialRepository.countByTipoAndSectorAndPeriod(
                eq("MZ-1-010"), eq("Insumo"), anyLong())).thenReturn(0L);

        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);
        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("con 1 dosis en 24h → ABORT_INSUMO")
    void conDosisEn24h_emiteAbortInsumo() {
        when(historialRepository.countByTipoAndSectorAndPeriod(
                eq("MZ-1-010"), eq("Insumo"), anyLong())).thenReturn(1L);

        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);
        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.ABORT_INSUMO);
        assertThat(acciones.get(0).motivo()).contains("1").contains("MZ-1-010");
    }

    @Test
    @DisplayName("sin configuración operativa → NOOP_INFO (fail-open)")
    void sinConfig_emiteNoopInfoFailOpen() {
        RuleContext ctx = new RuleContext(
                sector, zona, List.of(), List.of(),
                null,   // config = null
                "ok", java.time.Instant.now(), false, null, false);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ABORT_INSUMO es una acción bloqueante")
    void abortInsumoEsBloqueante() {
        assertThat(ActionType.ABORT_INSUMO.isBlocking()).isTrue();
    }
}
