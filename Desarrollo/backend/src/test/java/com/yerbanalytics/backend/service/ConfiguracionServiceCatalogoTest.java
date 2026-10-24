package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.parametros.ParametrosPorPieza;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.dto.Configuracion;
import com.yerbanalytics.backend.dto.ConfiguracionOperativa;
import com.yerbanalytics.backend.dto.UmbralMetrica;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosMediasombra;
import com.yerbanalytics.backend.exception.InvalidConfigurationException;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
import com.yerbanalytics.backend.repository.UmbralMetricaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tareas 5.1 y 5.2: el tiempo máximo de riego y la apertura máxima de la mediasombra se mudaron
 * al catálogo de parámetros de reglas; la configuración operativa ya no los lleva.
 */
@DisplayName("ConfiguracionService - mudanza al catálogo")
class ConfiguracionServiceCatalogoTest {

    private UmbralMetricaRepository umbrales;
    private ConfiguracionOperativaRepository operativaRepo;
    private HistorialService historial;
    private CatalogoParametrosService catalogo;
    private ConfiguracionService service;

    @BeforeEach
    void setUp() {
        umbrales = mock(UmbralMetricaRepository.class);
        operativaRepo = mock(ConfiguracionOperativaRepository.class);
        historial = mock(HistorialService.class);
        catalogo = mock(CatalogoParametrosService.class);
        when(umbrales.findAll()).thenReturn(List.of());
        when(operativaRepo.findById(1)).thenReturn(Optional.empty());
        when(operativaRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(catalogo.vigentes()).thenReturn(ParametrosPorPieza.uniforme(ReglaTestSupport.fabrica()));
        service = new ConfiguracionService(umbrales, operativaRepo, historial, catalogo);
    }

    private static List<UmbralMetrica> umbralesDeFabrica() {
        return NurseryConstants.SPECS.stream().map(sp -> new UmbralMetrica(sp.key(), sp.label(), sp.unit(), sp.dec(),
                sp.ideal()[0], sp.ideal()[1], sp.warn()[0], sp.warn()[1], sp.crit()[0], sp.crit()[1], false)).toList();
    }

    private static Configuracion cfg() {
        return cfg(null, null);
    }

    private static Configuracion cfg(String mediasombraDesde, String mediasombraHasta) {
        return new Configuracion(umbralesDeFabrica(), new ConfiguracionOperativa(240, "x", 1L, null, null,
                null, mediasombraDesde, mediasombraHasta));
    }

    // ------------------------------------------------------------------ 5.1

    @Test
    void getConfiguracion_laOperativaYaNoTraeLosCamposMudados() throws Exception {
        String json = new ObjectMapper().writeValueAsString(service.getConfiguracion().operativa());

        assertThat(json).doesNotContain("riegoTiempoMaxSeg").doesNotContain("mediasombraAperturaMaxPct");
        assertThat(json).doesNotContain("riegoVolMaxDiarioMl").contains("pasadaDiariaHora")
                // add-motor-reglas-operativo D-14: el seguimiento va al catálogo del motor y la dosis máxima se borró.
                .doesNotContain("insumoDosisMax24hMl").doesNotContain("seguimientoLatenciaMin")
                .doesNotContain("seguimientoDeltaMin")
                .doesNotContain("intervaloEvaluacionMinutos").doesNotContain("sensingPullIntervalSeconds");
    }

    @Test
    void laEntidadOperativaNoGuardaLosCamposMudados() {
        assertThat(ConfiguracionOperativaEntity.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("riegoTiempoMaxSeg", "mediasombraAperturaMaxPct", "insumoDosisMax24hMl",
                        "seguimientoLatenciaMin", "seguimientoDeltaMin");
    }

    @Test
    void updateConfiguracion_sinLosCamposMudadosGuarda() {
        service.updateConfiguracion(cfg(), "Ana");

        verify(operativaRepo).save(any(ConfiguracionOperativaEntity.class));
    }

    // ------------------------------------------------------------------ horario por defecto de la mediasombra

    @Test
    void horarioMediasombra_rechazaElCierreAntesDeLaApertura() {
        assertThatThrownBy(() -> service.updateConfiguracion(cfg("17:00", "08:00"), "Ana"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("posterior");
        verify(operativaRepo, never()).save(any());
    }

    @Test
    void horarioMediasombra_rechazaUnaHoraMalEscrita() {
        assertThatThrownBy(() -> service.updateConfiguracion(cfg("8", "17:00"), "Ana"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("HH:mm");
    }

    @Test
    void horarioMediasombra_seGuardaYSeLee() {
        service.updateConfiguracion(cfg("09:30", "16:00"), "Ana");

        org.mockito.ArgumentCaptor<ConfiguracionOperativaEntity> guardada =
                org.mockito.ArgumentCaptor.forClass(ConfiguracionOperativaEntity.class);
        verify(operativaRepo).save(guardada.capture());
        assertThat(guardada.getValue().getMediasombraAbiertaDesde()).isEqualTo("09:30");
        assertThat(guardada.getValue().getMediasombraAbiertaHasta()).isEqualTo("16:00");
    }

    @Test
    void horarioMediasombra_sinGuardarRigeElDeFabrica() {
        assertThat(service.getConfiguracion().operativa().mediasombraAbiertaDesde()).isEqualTo("08:00");
        assertThat(service.getConfiguracion().operativa().mediasombraAbiertaHasta()).isEqualTo("17:00");
        assertThat(service.horarioMediasombraPorDefecto().texto()).isEqualTo("08:00–17:00");
    }
}
