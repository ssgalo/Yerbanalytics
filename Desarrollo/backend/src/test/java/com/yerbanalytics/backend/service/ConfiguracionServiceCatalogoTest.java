package com.yerbanalytics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.dto.Configuracion;
import com.yerbanalytics.backend.dto.ConfiguracionOperativa;
import com.yerbanalytics.backend.dto.RustificacionEtapa;
import com.yerbanalytics.backend.dto.UmbralMetrica;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosMediasombra;
import com.yerbanalytics.backend.exception.InvalidConfigurationException;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
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
    private RustificacionEtapaRepository rustificacionRepo;
    private HistorialService historial;
    private CatalogoParametrosService catalogo;
    private ConfiguracionService service;

    @BeforeEach
    void setUp() {
        umbrales = mock(UmbralMetricaRepository.class);
        operativaRepo = mock(ConfiguracionOperativaRepository.class);
        rustificacionRepo = mock(RustificacionEtapaRepository.class);
        historial = mock(HistorialService.class);
        catalogo = mock(CatalogoParametrosService.class);
        when(umbrales.findAll()).thenReturn(List.of());
        when(operativaRepo.findById(1)).thenReturn(Optional.empty());
        when(rustificacionRepo.findAllByOrderByOrdenAsc()).thenReturn(List.of());
        when(operativaRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        service = new ConfiguracionService(umbrales, operativaRepo, rustificacionRepo, historial, catalogo);
    }

    private static List<UmbralMetrica> umbralesDeFabrica() {
        return NurseryConstants.SPECS.stream().map(sp -> new UmbralMetrica(sp.key(), sp.label(), sp.unit(), sp.dec(),
                sp.ideal()[0], sp.ideal()[1], sp.warn()[0], sp.warn()[1], sp.crit()[0], sp.crit()[1], false)).toList();
    }

    private static Configuracion cfg(List<RustificacionEtapa> etapas) {
        return new Configuracion(umbralesDeFabrica(),
                new ConfiguracionOperativa(15, 2, 5.0, 240, 5, "x", 1L), etapas);
    }

    // ------------------------------------------------------------------ 5.1

    @Test
    void getConfiguracion_laOperativaYaNoTraeLosCamposMudados() throws Exception {
        String json = new ObjectMapper().writeValueAsString(service.getConfiguracion().operativa());

        assertThat(json).doesNotContain("riegoTiempoMaxSeg").doesNotContain("mediasombraAperturaMaxPct");
        assertThat(json).doesNotContain("riegoVolMaxDiarioMl").contains("insumoDosisMax24hMl")
                .contains("seguimientoLatenciaMin").contains("intervaloEvaluacionMinutos");
    }

    @Test
    void laEntidadOperativaNoGuardaLosCamposMudados() {
        assertThat(ConfiguracionOperativaEntity.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("riegoTiempoMaxSeg", "mediasombraAperturaMaxPct");
    }

    @Test
    void updateConfiguracion_sinLosCamposMudadosGuarda() {
        service.updateConfiguracion(cfg(List.of()), "Ana");

        verify(operativaRepo).save(any(ConfiguracionOperativaEntity.class));
    }

    // ------------------------------------------------------------------ 5.2

    @Test
    void rustificacion_rechazaUnaEtapaPorEncimaDeLaAperturaMaximaDelCatalogo() {
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.con(Map.of(ParametrosMediasombra.APERTURA_MAXIMA, "60")));

        assertThatThrownBy(() -> service.updateConfiguracion(cfg(List.of(new RustificacionEtapa(1, 1, 10, 70))), "Ana"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("60");
        verify(operativaRepo, never()).save(any());
    }

    @Test
    void rustificacion_aceptaUnaEtapaIgualALaAperturaMaximaDelCatalogo() {
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.con(Map.of(ParametrosMediasombra.APERTURA_MAXIMA, "60")));

        service.updateConfiguracion(cfg(List.of(new RustificacionEtapa(1, 1, 10, 60))), "Ana");

        verify(operativaRepo).save(any(ConfiguracionOperativaEntity.class));
    }

    @Test
    void rustificacion_conLaFabricaDelCatalogoAceptaHastaCien() {
        service.updateConfiguracion(cfg(List.of(new RustificacionEtapa(1, 1, 10, 100))), "Ana");

        verify(operativaRepo).save(any(ConfiguracionOperativaEntity.class));
    }
}
