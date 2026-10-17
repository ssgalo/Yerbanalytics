package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.rules.ShadingRule;
import com.yerbanalytics.backend.engine.rules.StaleSensorRule;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * reglas_v2 S-02: sin lectura válida no corren las reglas que dependen de sensores, pero la mediasombra sigue
 * (M-01 por horario, M-02 con el pronóstico). {@link StaleSensorRule} sólo corta el riego.
 */
@DisplayName("StaleSensorRule no corta la mediasombra")
class StaleSensorMediasombraTest {

    private RuleOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        CatalogoParametrosService catalogo = mock(CatalogoParametrosService.class);
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        ShadingRule shading = new ShadingRule(mock(RustificacionEtapaRepository.class), "");
        orchestrator = new RuleOrchestrator(List.of(new StaleSensorRule(), shading), catalogo,
                new TrazaEvaluacionStore());
    }

    private ResultadoEvaluacion evaluateWithStaleReading() {
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        sector.setActuadorShade(50);
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-1");
        Instant now = Instant.now();
        zona.setLastReadingTime(now.minusSeconds(2 * 24 * 3600L).toEpochMilli());   // el nodo calló hace dos días
        zona.setHumSusTs(zona.getLastReadingTime());
        WeatherForecast uvPeak = new WeatherForecast(10.0, 9.5, now, 20.0, "Soleado", 40.0, List.of());
        RuleContext ctx = new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", now, uvPeak, false);
        return orchestrator.evaluate(ctx, OrigenEvaluacion.TELEMETRIA);
    }

    @Test
    @DisplayName("lectura vencida: el riego queda abortado y la mediasombra se evalúa y se mueve igual (M-02)")
    void staleReadingCutsIrrigationButNotShading() {
        ResultadoEvaluacion result = evaluateWithStaleReading();

        assertThat(result.acciones()).extracting(RuleAction::type)
                .contains(ActionType.ABORT_RIEGO, ActionType.MOVER_MEDIASOMBRA);
        assertThat(result.traza().reglas())
                .filteredOn(t -> t.ruleId().equals("ShadingRule"))
                .singleElement()
                .satisfies(t -> assertThat(t.estado()).isEqualTo(EstadoRegla.EVALUADA));
    }

    @Test
    @DisplayName("la regla de lectura vencida es global pero su corte es sólo de la rama de riego")
    void theCutIsOnlyForTheIrrigationBranch() {
        ResultadoEvaluacion result = evaluateWithStaleReading();

        assertThat(result.acciones()).noneMatch(a -> a.type() == ActionType.ABORT_ALL);
    }
}
