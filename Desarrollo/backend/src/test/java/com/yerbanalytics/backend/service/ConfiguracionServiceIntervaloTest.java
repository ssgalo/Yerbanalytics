package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.dto.Configuracion;
import com.yerbanalytics.backend.dto.ConfiguracionOperativa;
import com.yerbanalytics.backend.dto.UmbralMetrica;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.exception.InvalidConfigurationException;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
import com.yerbanalytics.backend.repository.UmbralMetricaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El intervalo de sensado define el ciclo de lectura del riego, que SE ACOTA a 60–360 min dentro del cálculo
 * del ciclo ({@code CicloLectura.acotarMinutos}); el guardado sigue exigiendo sólo un valor positivo. Validar
 * 60–360 al guardar rompería las bases con un valor viejo fuera de rango y le impediría al simulador emitir
 * más rápido que cada hora, que es la forma de probar el sistema.
 */
@DisplayName("ConfiguracionService - intervalo de sensado")
class ConfiguracionServiceIntervaloTest {

    private ConfiguracionOperativaRepository operativaRepo;
    private ConfiguracionService service;

    @BeforeEach
    void setUp() {
        UmbralMetricaRepository umbrales = mock(UmbralMetricaRepository.class);
        operativaRepo = mock(ConfiguracionOperativaRepository.class);
        RustificacionEtapaRepository rustificacionRepo = mock(RustificacionEtapaRepository.class);
        CatalogoParametrosService catalogo = mock(CatalogoParametrosService.class);
        when(umbrales.findAll()).thenReturn(List.of());
        when(operativaRepo.findById(1)).thenReturn(Optional.empty());
        when(rustificacionRepo.findAllByOrderByOrdenAsc()).thenReturn(List.of());
        when(operativaRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        service = new ConfiguracionService(umbrales, operativaRepo, rustificacionRepo,
                mock(HistorialService.class), catalogo);
    }

    private static Configuracion cfg(int intervaloSensadoMinutos) {
        List<UmbralMetrica> umbrales = NurseryConstants.SPECS.stream().map(sp -> new UmbralMetrica(sp.key(),
                sp.label(), sp.unit(), sp.dec(), sp.ideal()[0], sp.ideal()[1], sp.warn()[0], sp.warn()[1],
                sp.crit()[0], sp.crit()[1], false)).toList();
        return new Configuracion(umbrales,
                new ConfiguracionOperativa(2000, 15, 2, 5.0, intervaloSensadoMinutos, 5, "x", 1L), List.of());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -240})
    void rechazaUnIntervaloNoPositivo(int minutos) {
        assertThatThrownBy(() -> service.updateConfiguracion(cfg(minutos), "Ana"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("intervalo de sensado");
        verify(operativaRepo, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 59, 60, 240, 360, 361, 600})
    void aceptaCualquierIntervaloPositivoSinAcotarloAlGuardar(int minutos) {
        assertThatCode(() -> service.updateConfiguracion(cfg(minutos), "Ana")).doesNotThrowAnyException();
        ArgumentCaptor<ConfiguracionOperativaEntity> guardada = ArgumentCaptor.forClass(ConfiguracionOperativaEntity.class);
        verify(operativaRepo).save(guardada.capture());
        assertThat(guardada.getValue().getIntervaloSensadoMinutos()).isEqualTo(minutos);
    }
}
