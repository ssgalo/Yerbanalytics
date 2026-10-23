package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.supply.Supply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("S-06: qué acciones están bloqueadas por falta de efectividad")
class EffectivenessBlockServiceTest {

    private HistorialRepository history;
    private RuleOverrides overrides;
    private EffectivenessBlockService service;

    @BeforeEach
    void setUp() {
        history = mock(HistorialRepository.class);
        overrides = mock(RuleOverrides.class);
        when(overrides.isEnabled(anyString())).thenReturn(true);
        when(overrides.isBypassed(anyString(), anyString())).thenReturn(false);
        when(overrides.cooldownMinutes(anyString(), anyString())).thenReturn(Optional.empty());
        service = new EffectivenessBlockService(history, overrides);
    }

    private static HistorialEventoEntity blocking(EffectivenessKind kind, String sectorId) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId("ev-" + kind + "-" + sectorId);
        e.setSectorId(sectorId);
        e.setZonaId("MZ-1");
        e.setEfectividadTipo(kind.name());
        e.setBloqueoRepeticion(true);
        return e;
    }

    @Test
    void unRiegoSinEfectividadBloqueaElRiegoDeTodosLosSectoresDeLaZona() {
        when(history.findByZonaIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull("MZ-1"))
                .thenReturn(List.of(blocking(EffectivenessKind.IRRIGATION, "MZ-1-010")));

        Map<String, EffectivenessBlocks> blocks = service.forZone("MZ-1", List.of("MZ-1-001", "MZ-1-045"));

        assertThat(blocks.get("MZ-1-001").blocks(EffectivenessKind.IRRIGATION)).isTrue();
        assertThat(blocks.get("MZ-1-045").blocks(EffectivenessKind.IRRIGATION)).isTrue();
        assertThat(blocks.get("MZ-1-045").blocks(EffectivenessKind.FUNGICIDE)).isFalse();
    }

    @Test
    void unTratamientoSinEfectividadBloqueaSoloAlSectorYSoloEseTipo() {
        when(history.findByZonaIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull("MZ-1"))
                .thenReturn(List.of(blocking(EffectivenessKind.CORRECTIVE_FERTILIZER, "MZ-1-045")));

        Map<String, EffectivenessBlocks> blocks = service.forZone("MZ-1", List.of("MZ-1-001", "MZ-1-045"));

        assertThat(blocks.get("MZ-1-045").blocks(EffectivenessKind.CORRECTIVE_FERTILIZER)).isTrue();
        assertThat(blocks.get("MZ-1-045").blocks(EffectivenessKind.FUNGICIDE)).isFalse();
        assertThat(blocks.get("MZ-1-045").blocks(EffectivenessKind.IRRIGATION)).isFalse();
        assertThat(blocks.get("MZ-1-001").blocks(EffectivenessKind.CORRECTIVE_FERTILIZER)).isFalse();
    }

    @Test
    void sinBloqueosTodosLosSectoresFiguranLibres() {
        when(history.findByZonaIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull("MZ-1")).thenReturn(List.of());

        Map<String, EffectivenessBlocks> blocks = service.forZone("MZ-1", List.of("MZ-1-001"));

        assertThat(blocks.get("MZ-1-001")).isEqualTo(EffectivenessBlocks.NONE);
    }

    @Test
    void conElServicioApagadoNoSeBloqueaNada() {
        when(overrides.isEnabled(EffectivenessService.NAME)).thenReturn(false);
        when(history.findByZonaIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull("MZ-1"))
                .thenReturn(List.of(blocking(EffectivenessKind.IRRIGATION, "MZ-1-010")));

        assertThat(service.forZone("MZ-1", List.of("MZ-1-001")).get("MZ-1-001")).isEqualTo(EffectivenessBlocks.NONE);
    }

    @Test
    void elFitosanitarioSinEfectividadFrenaOtroFitosanitarioPeroNoElFertilizanteDelPlan() {
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-045");
        sector.setZona(RuleContextTestFactory.zonaBasica("MZ-1"));
        when(history.findBySectorIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull("MZ-1-045"))
                .thenReturn(List.of(blocking(EffectivenessKind.FUNGICIDE, "MZ-1-045")));

        assertThat(service.supplyBlock(sector, Supply.FUNGICIDE, "FungalDamageRule")).isPresent()
                .get().asString().contains("fitosanitario");
        assertThat(service.supplyBlock(sector, Supply.FERTILIZER, "NutritionPlanRule")).isEmpty();
    }

    @Test
    void elFertilizanteCorrectivoSinEfectividadFrenaAlCorrectivoPeroNoAlPlanDeNutricion() {
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-045");
        sector.setZona(RuleContextTestFactory.zonaBasica("MZ-1"));
        when(history.findBySectorIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull("MZ-1-045"))
                .thenReturn(List.of(blocking(EffectivenessKind.CORRECTIVE_FERTILIZER, "MZ-1-045")));

        assertThat(service.supplyBlock(sector, Supply.FERTILIZER, "ChlorosisNutritionalRule")).isPresent();
        assertThat(service.supplyBlock(sector, Supply.FERTILIZER, "NutritionPlanRule")).isEmpty();
    }

    @Test
    void elBypassDeLaReglaYSuApagadoDejanPasarLaAplicacion() {
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-045");
        sector.setZona(RuleContextTestFactory.zonaBasica("MZ-1"));
        when(history.findBySectorIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull("MZ-1-045"))
                .thenReturn(List.of(blocking(EffectivenessKind.FUNGICIDE, "MZ-1-045")));

        when(overrides.isBypassed(EffectivenessBlockService.RULE_NAME, EffectivenessBlockService.BYPASS_BLOCK)).thenReturn(true);
        assertThat(service.supplyBlock(sector, Supply.FUNGICIDE, "FungalDamageRule")).isEmpty();

        when(overrides.isBypassed(EffectivenessBlockService.RULE_NAME, EffectivenessBlockService.BYPASS_BLOCK)).thenReturn(false);
        when(overrides.isEnabled(EffectivenessBlockService.RULE_NAME)).thenReturn(false);
        assertThat(service.supplyBlock(sector, Supply.FUNGICIDE, "FungalDamageRule")).isEmpty();
    }
}
