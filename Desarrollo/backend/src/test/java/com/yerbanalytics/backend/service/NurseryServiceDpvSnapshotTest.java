package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.dto.LecturaZona;
import com.yerbanalytics.backend.dto.NurseryData;
import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.riego.DespachoRiego;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.readings.ReadingRecorder;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.sensing.PulsoSensadoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El snapshot expone el DPV de la zona con su tramo, y sin dato lo deja vacío (nunca en cero). */
@DisplayName("NurseryService.getSnapshot - DPV de la zona")
class NurseryServiceDpvSnapshotTest {

    private static final Instant NOW = Instant.parse("2026-10-14T13:00:00Z");

    private ZonaEntity zone;
    private NurseryService service;

    @BeforeEach
    void setUp() {
        ZonaRepository zoneRepository = mock(ZonaRepository.class);
        CatalogoParametrosService parameters = mock(CatalogoParametrosService.class);
        when(parameters.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        zone = RuleContextTestFactory.zonaBasica("MZ-1");
        zone.setLastReadingTime(NOW.toEpochMilli() - 60_000L);
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        sector.setZona(zone);
        zone.setSectors(List.of(sector));
        when(zoneRepository.findAllWithSectors()).thenReturn(List.of(zone));
        ConfiguracionService configuration = mock(ConfiguracionService.class);
        when(configuration.getConfiguracionOperativa()).thenReturn(RuleContextTestFactory.defaultConfig());
        when(configuration.getEffectiveSpecs()).thenReturn(NurseryConstants.SPECS);

        service = new NurseryService(new NurseryProperties(), zoneRepository, mock(SectorRepository.class),
                mock(HistorialService.class), configuration, mock(HardwareService.class),
                mock(TopologiaLayoutRepository.class),
                new RuleOrchestrator(List.of(), parameters, new TrazaEvaluacionStore()), mock(ActionExecutor.class),
                mock(WeatherService.class), mock(ManualLockRepository.class), mock(DiagnosticoService.class),
                new TrazaEvaluacionStore(), mock(HistorialRepository.class), mock(DespachoRiego.class),
                mock(PulsoSensadoService.class), mock(EscalamientoNodoService.class), mock(ReadingRecorder.class),
                ReadingGuardsDePrueba.aceptaTodo(), Clock.fixed(NOW, ZonaHorariaVivero.ZONA), 20);
    }

    private LecturaZona reading() {
        NurseryData data = service.getSnapshot();
        return data.zonas().get(0).lectura();
    }

    @Test
    @DisplayName("calor seco: 2,2 kPa se expone con su tramo y su etiqueta")
    void dryHeat() {
        zone.setDpvRaw(2.2);

        assertThat(reading().dpv()).satisfies(d -> {
            assertThat(d.value()).isEqualTo(2.2);
            assertThat(d.band()).isEqualTo("DRY_HEAT");
            assertThat(d.label()).isEqualTo("calor seco");
        });
    }

    @Test
    @DisplayName("sin DPV en la zona no hay valor (no un cero)")
    void noData() {
        zone.setDpvRaw(null);

        assertThat(reading().dpv()).isNull();
    }

    @Test
    @DisplayName("con el nodo fuera de servicio el DPV no se muestra como vigente")
    void offlineNode() {
        zone.setDpvRaw(1.0);
        zone.setPulsosSinRespuesta(3);

        assertThat(reading().stale()).isTrue();
        assertThat(reading().dpv()).isNull();
    }
}
