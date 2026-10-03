package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El historial toma la hora del {@link Clock} del vivero y no de {@code System.currentTimeMillis()}:
 * el despacho de riego y los eventos que consulta comparan contra el mismo reloj.
 */
@DisplayName("HistorialService - reloj y zona horaria del vivero")
class HistorialServiceRelojTest {

    /** 01:00 UTC del 3/10 son las 22:00 del 2/10 en Buenos Aires. */
    private static final Instant AHORA = Instant.parse("2026-10-03T01:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZonaHorariaVivero.ZONA);

    private HistorialRepository historialRepository;
    private SectorRepository sectorRepository;
    private HistorialService service;
    private SectorEntity sector;

    @BeforeEach
    void setUp() {
        historialRepository = mock(HistorialRepository.class);
        sectorRepository = mock(SectorRepository.class);
        ConfiguracionService config = mock(ConfiguracionService.class);
        when(config.getLatencyMs()).thenReturn(120_000L);
        when(config.getLatencyLabel()).thenReturn("2 min");
        when(config.getUmbralRecuperacion()).thenReturn(5.0);
        service = new HistorialService(historialRepository, sectorRepository, config, 1, "x", 1, RELOJ);
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
    void laInaccionSellaConElRelojDelVivero() {
        service.registrarInaccion(sector, ActionType.NOOP_INFO, "RiegoPorDeficitRule", "motivo");

        assertThat(guardado().getTs()).isEqualTo(AHORA.toEpochMilli());
    }

    @Test
    void elInsumoSellaConElRelojDelVivero() {
        service.registrarInsumo(sector);

        assertThat(guardado().getTs()).isEqualTo(AHORA.toEpochMilli());
    }

    @Test
    void elSeguimientoVenceContraElRelojDelVivero() {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId("h1");
        e.setSectorId("MZ-2-003");
        e.setTs(AHORA.toEpochMilli() - 119_000L);   // la latencia (120 s) todavía no venció
        e.setLatencyMs(120_000L);
        e.setMetricKey("humSus");
        e.setValorAntes(40.0);
        when(historialRepository.findByEvoShowTrueAndEvoEvaluadoTsIsNull()).thenReturn(List.of(e));
        sector.getZona().setHumSusRaw(60.0);
        when(sectorRepository.findById("MZ-2-003")).thenReturn(java.util.Optional.of(sector));

        service.evaluarSeguimiento();
        assertThat(e.getEvoEvaluadoTs()).isNull();

        e.setTs(AHORA.toEpochMilli() - 121_000L);   // ahora sí venció
        service.evaluarSeguimiento();
        assertThat(e.getEvoEvaluadoTs()).isEqualTo(AHORA.toEpochMilli());
    }

    @Test
    void elInicioDelDiaDeLosKpiEsMedianocheEnBuenosAiresNoEnLaZonaDelJvm() {
        when(historialRepository.countByTipoSinceTs(any(), anyLong())).thenReturn(0L);

        service.countToday();

        // El día local es el 2/10: su medianoche en Buenos Aires (UTC-3) son las 03:00 UTC.
        long medianocheLocal = Instant.parse("2026-10-02T03:00:00Z").toEpochMilli();
        verify(historialRepository).countByTipoSinceTs(eq("Riego"), eq(medianocheLocal));
    }
}
