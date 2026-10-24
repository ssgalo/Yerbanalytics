package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.parametros.ParametrosPorPieza;
import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.dto.Configuracion;
import com.yerbanalytics.backend.dto.ConfiguracionOperativa;
import com.yerbanalytics.backend.dto.UmbralMetrica;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.exception.InvalidConfigurationException;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
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
 * El intervalo de sensado es el del pulso (y el del ciclo de lectura del riego): se guarda sólo dentro de 60–360 min.
 * Una base vieja con un valor fuera de rango se sigue leyendo acotada. La pasada diaria se guarda con hora
 * {@code HH:mm}; los nulos se leen como valores de fábrica.
 */
@DisplayName("ConfiguracionService - intervalo de sensado y pasada diaria")
class ConfiguracionServiceIntervaloTest {

    private ConfiguracionOperativaRepository operativaRepo;
    private ConfiguracionService service;
    private CatalogoParametrosService catalogo;

    @BeforeEach
    void setUp() {
        UmbralMetricaRepository umbrales = mock(UmbralMetricaRepository.class);
        operativaRepo = mock(ConfiguracionOperativaRepository.class);
        catalogo = mock(CatalogoParametrosService.class);
        when(umbrales.findAll()).thenReturn(List.of());
        when(operativaRepo.findById(1)).thenReturn(Optional.empty());
        when(operativaRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(catalogo.vigentes()).thenReturn(ParametrosPorPieza.uniforme(ReglaTestSupport.fabrica()));
        service = new ConfiguracionService(umbrales, operativaRepo,
                mock(HistorialService.class), catalogo);
    }

    private static Configuracion cfg(int intervaloSensadoMinutos) {
        return cfg(intervaloSensadoMinutos, null, null);
    }

    private static Configuracion cfg(int intervaloSensadoMinutos, Boolean pasadaActiva, String pasadaHora) {
        List<UmbralMetrica> umbrales = NurseryConstants.SPECS.stream().map(sp -> new UmbralMetrica(sp.key(),
                sp.label(), sp.unit(), sp.dec(), sp.ideal()[0], sp.ideal()[1], sp.warn()[0], sp.warn()[1],
                sp.crit()[0], sp.crit()[1], false)).toList();
        return new Configuracion(umbrales,
                new ConfiguracionOperativa(intervaloSensadoMinutos, "x", 1L, pasadaActiva, pasadaHora, null));
    }

    private ConfiguracionOperativaEntity savedEntity() {
        ArgumentCaptor<ConfiguracionOperativaEntity> saved = ArgumentCaptor.forClass(ConfiguracionOperativaEntity.class);
        verify(operativaRepo).save(saved.capture());
        return saved.getValue();
    }

    // --- intervalo del pulso ---

    @ParameterizedTest
    @ValueSource(ints = {0, 30, 59, 361, 600})
    void rechazaUnIntervaloFueraDe60a360NombrandoElParametro(int minutos) {
        assertThatThrownBy(() -> service.updateConfiguracion(cfg(minutos), "Ana"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("intervaloSensadoMinutos");
        verify(operativaRepo, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {60, 120, 240, 360})
    void aceptaUnIntervaloEnRango(int minutos) {
        assertThatCode(() -> service.updateConfiguracion(cfg(minutos), "Ana")).doesNotThrowAnyException();
        assertThat(savedEntity().getIntervaloSensadoMinutos()).isEqualTo(minutos);
    }

    // --- pasada diaria ---

    @ParameterizedTest
    @ValueSource(strings = {"25:00", "9:5", "", "10:60", "1030", "ab:cd"})
    void rechazaUnaHoraInvalidaNombrandoElParametro(String hora) {
        assertThatThrownBy(() -> service.updateConfiguracion(cfg(240, true, hora), "Ana"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("pasadaDiariaHora");
        verify(operativaRepo, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "10:30", "23:59"})
    void aceptaUnaHoraValida(String hora) {
        service.updateConfiguracion(cfg(240, true, hora), "Ana");

        ConfiguracionOperativaEntity e = savedEntity();
        assertThat(e.getPasadaDiariaHora()).isEqualTo(hora);
        assertThat(e.getPasadaDiariaActiva()).isTrue();
    }

    @org.junit.jupiter.api.Test
    void sinCamposDeLaPasadaConservaLoGuardado() {
        ConfiguracionOperativaEntity stored = new ConfiguracionOperativaEntity();
        stored.setId(1);
        stored.setPasadaDiariaActiva(true);
        stored.setPasadaDiariaHora("09:15");
        when(operativaRepo.findById(1)).thenReturn(Optional.of(stored));

        service.updateConfiguracion(cfg(240, null, null), "Ana");

        assertThat(savedEntity().getPasadaDiariaActiva()).isTrue();
        assertThat(savedEntity().getPasadaDiariaHora()).isEqualTo("09:15");
    }

    @org.junit.jupiter.api.Test
    void nulosSeLeenComoValoresDeFabrica() {
        ConfiguracionOperativaEntity stored = new ConfiguracionOperativaEntity();
        stored.setId(1);
        stored.setIntervaloSensadoMinutos(null);
        when(operativaRepo.findById(1)).thenReturn(Optional.of(stored));

        ConfiguracionOperativa op = service.getConfiguracion().operativa();

        assertThat(op.intervaloSensadoMinutos()).isEqualTo(240);
        assertThat(op.pasadaDiariaActiva()).isFalse();
        assertThat(op.pasadaDiariaHora()).isEqualTo("11:00");
    }

    @org.junit.jupiter.api.Test
    void laPasadaIdaYVueltaPorElDto() {
        // El repositorio se comporta como una tabla: lo guardado es lo que devuelve findById después.
        java.util.concurrent.atomic.AtomicReference<ConfiguracionOperativaEntity> table =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(operativaRepo.save(any())).thenAnswer(i -> {
            table.set(i.getArgument(0));
            return i.getArgument(0);
        });
        when(operativaRepo.findById(1)).thenAnswer(i -> Optional.ofNullable(table.get()));

        Configuracion returned = service.updateConfiguracion(cfg(240, true, "10:30"), "Ana");

        assertThat(returned.operativa().pasadaDiariaActiva()).isTrue();
        assertThat(service.getConfiguracion().operativa().pasadaDiariaHora()).isEqualTo("10:30");
    }

    @org.junit.jupiter.api.Test
    void exponeElUmbralEfectivoDelCatalogo() {
        // ReglaTestSupport.fabrica() tiene el piso de 90 s y el catálogo simulado lo devuelve tal cual.
        assertThat(service.getConfiguracion().operativa().effectiveStaleThresholdSeconds()).isEqualTo(90);
    }

    @org.junit.jupiter.api.Test
    void guardarInvalidaElCatalogo() {
        service.updateConfiguracion(cfg(240), "Ana");

        verify(catalogo).invalidarAlTerminar();
    }
}
