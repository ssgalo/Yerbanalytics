package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.HistorialEvento;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El historial dibuja lo que corrió entonces, no lo que está prendido hoy: cada decisión del motor guarda qué piezas
 * estaban apagadas (estado efectivo) en el momento de registrarla.
 */
@DisplayName("HistorialService · reglasInactivas")
class HistorialServiceReglasInactivasTest {

    private HistorialRepository historialRepository;
    private RuleOverrides overrides;
    private HistorialService service;
    private SectorEntity sector;

    @BeforeEach
    void setUp() {
        historialRepository = mock(HistorialRepository.class);
        overrides = mock(RuleOverrides.class);
        service = new HistorialService(historialRepository, kind -> 1_800_000L, Clock.systemUTC(), overrides);
        sector = RuleContextTestFactory.sectorBasico("MZ-2-003");
        sector.setZona(RuleContextTestFactory.zonaBasica("MZ-2"));
        sector.getZona().setName("Macro-zona 2");
    }

    private HistorialEventoEntity guardado() {
        ArgumentCaptor<HistorialEventoEntity> c = ArgumentCaptor.forClass(HistorialEventoEntity.class);
        verify(historialRepository).save(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("un riego del motor guarda las piezas apagadas en ese momento, ordenadas y separadas por coma")
    void unRiegoGuardaLasApagadas() {
        when(overrides.inactiveNames()).thenReturn(Set.of("SupplyRule", "FueraDeVentanaRiegoRule"));

        service.registrarRiego(sector, new DetalleRiego(4.2, 504, 44.0, false), "RiegoPorDeficitRule", 1_234L);

        assertThat(guardado().getReglasInactivas()).isEqualTo("FueraDeVentanaRiegoRule,SupplyRule");
    }

    @Test
    @DisplayName("sin nada apagado guarda vacío (distinto de null, que es 'evento anterior a este registro')")
    void sinNadaApagadoGuardaVacio() {
        when(overrides.inactiveNames()).thenReturn(Set.of());

        service.registrarRiego(sector, new DetalleRiego(4.2, 504, 44.0, false), "RiegoPorDeficitRule", 1_234L);

        assertThat(guardado().getReglasInactivas()).isEmpty();
    }

    @Test
    @DisplayName("las alertas, los insumos y la mediasombra del motor también lo guardan")
    void otrasDecisionesDelMotor() {
        when(overrides.inactiveNames()).thenReturn(Set.of("PausaTrasAplicacionRule"));

        service.registrarAlerta("MZ-2", "Macro-zona 2", "DeficitCriticoRule",
                new DetalleAlerta(NivelAlerta.CRITICAL, "Déficit hídrico crítico"), 1L);
        service.registrarInsumo(sector, 10, "Fertilizante", "NutritionPlanRule");
        service.registrarMediasombra("MZ-2", "Macro-zona 2", "ShadingRule", "l", "d", "a", "Efectiva", 2L);

        ArgumentCaptor<HistorialEventoEntity> c = ArgumentCaptor.forClass(HistorialEventoEntity.class);
        verify(historialRepository, org.mockito.Mockito.times(3)).save(c.capture());
        assertThat(c.getAllValues()).extracting(HistorialEventoEntity::getReglasInactivas)
                .containsOnly("PausaTrasAplicacionRule");
    }

    @Test
    @DisplayName("un evento que no es una decisión del motor no lo guarda")
    void lasConfiguracionesNoLoGuardan() {
        when(overrides.inactiveNames()).thenReturn(Set.of("SupplyRule"));

        service.registrarEstadoRegla("Ana", "SupplyRule", "Dosificación de insumo", "", true, null, null);

        assertThat(guardado().getReglasInactivas()).isNull();
    }

    private HistorialEvento dto(String reglasInactivas) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId("e1");
        e.setSectorId("MZ-2-001");
        e.setZonaId("MZ-2");
        e.setZonaName("Macro-zona 2");
        e.setTipo("Riego");
        e.setTs(System.currentTimeMillis());
        e.setLectura("l");
        e.setDecision("d");
        e.setAccion("a");
        e.setRes("Efectiva");
        e.setSev("—");
        e.setRegla("RiegoPorDeficitRule");
        e.setReglasInactivas(reglasInactivas);
        when(historialRepository.findFiltered(any(), any(), any(), any(), any(), any())).thenReturn(List.of(e));
        return service.getHistorial(null, null, null, null, null, null).get(0);
    }

    @Test
    @DisplayName("el DTO expone reglasInactivas como lista; vacío es lista vacía y null sigue siendo null")
    void elDtoLoExponeComoLista() {
        assertThat(dto("FueraDeVentanaRiegoRule,SupplyRule").reglasInactivas())
                .containsExactly("FueraDeVentanaRiegoRule", "SupplyRule");
        assertThat(dto("").reglasInactivas()).isEmpty();
        assertThat(dto(null).reglasInactivas()).isNull();
    }
}
