package com.yerbanalytics.backend.service;

import java.time.Clock;
import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tarea 8.6: {@code registrarRiego(sector, detalle, regla, ts)} guarda lo ordenado y mantiene el seguimiento. */
@DisplayName("HistorialService.registrarRiego(sector, detalle, regla, ts)")
class HistorialServiceRiegoTest {

    private HistorialRepository historialRepository;
    private HistorialService service;
    private SectorEntity sector;

    @BeforeEach
    void setUp() {
        historialRepository = mock(HistorialRepository.class);
        ConfiguracionService config = mock(ConfiguracionService.class);
        when(config.getLatencyMs()).thenReturn(120_000L);
        when(config.getLatencyLabel()).thenReturn("2 min");
        when(config.getUmbralRecuperacion()).thenReturn(5.0);
        service = new HistorialService(historialRepository, mock(SectorRepository.class), config, 1, "x", 1, Clock.systemUTC());
        sector = RuleContextTestFactory.sectorBasico("MZ-2-003");
        sector.setZona(RuleContextTestFactory.zonaBasica("MZ-2"));
        sector.getZona().setName("Macro-zona 2");
    }

    private HistorialEventoEntity registrar(DetalleRiego d, String regla, long ts) {
        service.registrarRiego(sector, d, regla, ts);
        ArgumentCaptor<HistorialEventoEntity> c = ArgumentCaptor.forClass(HistorialEventoEntity.class);
        verify(historialRepository).save(c.capture());
        return c.getValue();
    }

    @Test
    void guardaVolumenDuracionReglaYMarcaDeTiempoDeLaOrden() {
        HistorialEventoEntity e = registrar(new DetalleRiego(4.2, 504, 44.0, false), "RiegoPorDeficitRule", 1_234L);

        assertThat(e.getTipo()).isEqualTo("Riego");
        assertThat(e.getSectorId()).isEqualTo("MZ-2-003");
        assertThat(e.getZonaId()).isEqualTo("MZ-2");
        assertThat(e.getTs()).isEqualTo(1_234L);
        assertThat(e.getRegla()).isEqualTo("RiegoPorDeficitRule");
        assertThat(e.getVolumenL()).isEqualTo(4.2);
        assertThat(e.getDuracionSeg()).isEqualTo(504);
        assertThat(e.getAlerta()).isNull();
        assertThat(e.getRes()).isEqualTo("Efectiva");
    }

    @Test
    void laCadenaDeJustificacionDiceHumedadVolumenYDuracion() {
        HistorialEventoEntity e = registrar(new DetalleRiego(4.2, 504, 44.0, false), "RiegoPorDeficitRule", 1L);

        assertThat(e.getLectura()).contains("44").contains("RiegoPorDeficitRule");
        assertThat(e.getDecision()).contains("4,2 L").contains("504 s");
        assertThat(e.getAccion()).isNotBlank();
    }

    @Test
    void siLaDuracionSeRecortoLoDice() {
        HistorialEventoEntity e = registrar(new DetalleRiego(10.0, 1200, 30.0, true), "DeficitCriticoRule", 1L);

        assertThat(e.getDecision()).contains("recort");
    }

    @Test
    void mantieneElSeguimientoDeLaHumedadDeSustrato() {
        HistorialEventoEntity e = registrar(new DetalleRiego(4.2, 504, 44.0, false), "RiegoPorDeficitRule", 1L);

        assertThat(e.isEvoShow()).isTrue();
        assertThat(e.getMetricKey()).isEqualTo("humSus");
        assertThat(e.getValorAntes()).isEqualTo(44.0);
        assertThat(e.getEvoAntes()).isEqualTo("44");
        assertThat(e.getEvoVerdict()).isEqualTo("En seguimiento");
        assertThat(e.getLatencyMs()).isEqualTo(120_000L);
        assertThat(e.getUmbralRecuperacion()).isEqualTo(5.0);
        assertThat(e.getEvoEvaluadoTs()).isNull();
    }
}
