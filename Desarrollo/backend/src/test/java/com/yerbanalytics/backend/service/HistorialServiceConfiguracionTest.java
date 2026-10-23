package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosSeguimiento;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Tarea 2.3: el overload de {@code registrarConfiguracion} con detalle. */
@DisplayName("HistorialService.registrarConfiguracion(usuario, detalle)")
@ExtendWith(MockitoExtension.class)
class HistorialServiceConfiguracionTest {

    @Mock
    private HistorialRepository historialRepository;
    @Mock
    private SectorRepository sectorRepository;
    @Mock
    private CatalogoParametrosService parametros;

    @Test
    void asientaUnEventoConfiguracionConElUsuarioYElDetalle() {
        HistorialService service = new HistorialService(historialRepository, sectorRepository,
                parametros, java.time.Clock.systemUTC());

        service.registrarConfiguracion("Ana", "Parámetros de reglas modificados: riego.umbral-humedad.");

        ArgumentCaptor<HistorialEventoEntity> captor = ArgumentCaptor.forClass(HistorialEventoEntity.class);
        verify(historialRepository).save(captor.capture());
        HistorialEventoEntity e = captor.getValue();
        assertThat(e.getTipo()).isEqualTo("Configuración");
        assertThat(e.getLectura()).contains("Ana");
        assertThat(e.getAccion()).contains("riego.umbral-humedad");
        assertThat(e.getSectorId()).isEqualTo("—");
        assertThat(e.isEvoShow()).isFalse();
    }

    @Test
    void laRecalibracionDelCriterioDeDiagnosticoQuedaConSuUsuario() {
        HistorialService service = new HistorialService(historialRepository, sectorRepository,
                parametros, java.time.Clock.systemUTC());

        service.registrarCriterioDiagnostico("Ana", "Criterio de diagnóstico actualizado.");

        ArgumentCaptor<HistorialEventoEntity> captor = ArgumentCaptor.forClass(HistorialEventoEntity.class);
        verify(historialRepository).save(captor.capture());
        HistorialEventoEntity e = captor.getValue();
        assertThat(e.getTipo()).isEqualTo("Configuración");
        assertThat(e.getLectura()).contains("criterio de diagnóstico").contains("Ana");
        assertThat(e.getAccion()).isEqualTo("Criterio de diagnóstico actualizado.");
    }

    // ------------------------------------------------------------------ add-motor-reglas-operativo (D-14)

    private HistorialEventoEntity riego(CatalogoParametrosService parametros) {
        HistorialService service = new HistorialService(historialRepository, sectorRepository, parametros,
                java.time.Clock.systemUTC());
        var sector = com.yerbanalytics.backend.engine.RuleContextTestFactory.sectorBasico("MZ-2-003");
        sector.setZona(com.yerbanalytics.backend.engine.RuleContextTestFactory.zonaBasica("MZ-2"));

        service.registrarRiego(sector, new com.yerbanalytics.backend.engine.DetalleRiego(4.2, 504, 44.0, false),
                "RiegoPorDeficitRule", 1_234L);

        ArgumentCaptor<HistorialEventoEntity> captor = ArgumentCaptor.forClass(HistorialEventoEntity.class);
        verify(historialRepository).save(captor.capture());
        return captor.getValue();
    }

    private static CatalogoParametrosService conValores(java.util.Map<DefinicionParametro, String> overrides) {
        CatalogoParametrosService parametros = org.mockito.Mockito.mock(CatalogoParametrosService.class);
        org.mockito.Mockito.when(parametros.vigentes()).thenReturn(ReglaTestSupport.con(overrides));
        return parametros;
    }

    @Test
    @DisplayName("la latencia y el delta del seguimiento salen del catálogo (fábrica 2 min y 5)")
    void elSeguimientoSaleDelCatalogo() {
        HistorialEventoEntity e = riego(conValores(java.util.Map.of()));

        assertThat(e.getLatencyMs()).isEqualTo(2 * 60_000L);
        assertThat(e.getEvoLatencia()).isEqualTo("2 min");
        assertThat(e.getUmbralRecuperacion()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("un override del catálogo se usa: latencia de 10 min y delta de 8,5")
    void unOverrideDelCatalogoSeUsa() {
        HistorialEventoEntity e = riego(conValores(java.util.Map.of(
                ParametrosSeguimiento.LATENCY_MIN, "10", ParametrosSeguimiento.DELTA_MIN, "8.5")));

        assertThat(e.getLatencyMs()).isEqualTo(10 * 60_000L);
        assertThat(e.getEvoLatencia()).isEqualTo("10 min");
        assertThat(e.getUmbralRecuperacion()).isEqualTo(8.5);
    }

    @Test
    @DisplayName("si el catálogo no se puede leer, el seguimiento se registra igual con los valores de fábrica")
    void sinCatalogoSeUsaLaFabrica() {
        CatalogoParametrosService roto = org.mockito.Mockito.mock(CatalogoParametrosService.class);
        org.mockito.Mockito.when(roto.vigentes()).thenThrow(new IllegalStateException("base caída"));

        HistorialEventoEntity e = riego(roto);

        assertThat(e.getLatencyMs()).isEqualTo(2 * 60_000L);
        assertThat(e.getUmbralRecuperacion()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("HistorialService declara los dos parámetros que lee")
    void declaraLosParametrosQueLee() {
        HistorialService service = new HistorialService(historialRepository, sectorRepository,
                org.mockito.Mockito.mock(CatalogoParametrosService.class), java.time.Clock.systemUTC());

        assertThat(service.name()).isEqualTo("HistorialService");
        assertThat(service.parametros()).containsExactly(ParametrosSeguimiento.LATENCY_MIN, ParametrosSeguimiento.DELTA_MIN);
    }
}
