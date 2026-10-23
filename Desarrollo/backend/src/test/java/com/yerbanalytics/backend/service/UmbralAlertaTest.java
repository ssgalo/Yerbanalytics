package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.dto.Alert;
import com.yerbanalytics.backend.dto.Configuracion;
import com.yerbanalytics.backend.dto.ConfiguracionOperativa;
import com.yerbanalytics.backend.dto.LecturaZona;
import com.yerbanalytics.backend.dto.Metric;
import com.yerbanalytics.backend.dto.MetricSpec;
import com.yerbanalytics.backend.dto.UmbralMetrica;
import com.yerbanalytics.backend.dto.Zona;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.exception.InvalidConfigurationException;
import com.yerbanalytics.backend.model.UmbralMetricaEntity;
import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
import com.yerbanalytics.backend.repository.UmbralMetricaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Punto de alerta por métrica (add-umbral-alerta-metricas): valor por defecto, validación y alertas. */
@DisplayName("Umbral de alerta por métrica")
class UmbralAlertaTest {

    private UmbralMetricaRepository umbrales;
    private ConfiguracionService service;

    @BeforeEach
    void setUp() {
        umbrales = mock(UmbralMetricaRepository.class);
        ConfiguracionOperativaRepository operativaRepo = mock(ConfiguracionOperativaRepository.class);
        CatalogoParametrosService catalogo = mock(CatalogoParametrosService.class);
        when(umbrales.findAll()).thenReturn(List.of());
        when(umbrales.findById(any())).thenReturn(Optional.empty());
        when(operativaRepo.findById(1)).thenReturn(Optional.empty());
        when(operativaRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        service = new ConfiguracionService(umbrales, operativaRepo,
                mock(HistorialService.class), catalogo);
    }

    private static UmbralMetrica humAmb(Double alertMin, Double alertMax) {
        return new UmbralMetrica("humAmb", "Humedad ambiental", "%", 0,
                62, 84, 52, 91, 42, 96, false, alertMin, alertMax);
    }

    /** Fábrica para todas las métricas, salvo humAmb, que se reemplaza. */
    private static Configuracion cfgCon(UmbralMetrica humAmb) {
        List<UmbralMetrica> lista = NurseryConstants.SPECS.stream()
                .map(sp -> "humAmb".equals(sp.key()) ? humAmb : new UmbralMetrica(sp.key(), sp.label(), sp.unit(),
                        sp.dec(), sp.ideal()[0], sp.ideal()[1], sp.warn()[0], sp.warn()[1], sp.crit()[0], sp.crit()[1], false))
                .toList();
        return new Configuracion(lista, new ConfiguracionOperativa(15, 2, 5.0, 240, "x", 1L, null, null, null));
    }

    @Test
    void sinFilaElPuntoEsElMedioDeLaFranja() {
        UmbralMetrica u = service.getConfiguracion().umbrales().stream()
                .filter(x -> "humAmb".equals(x.key())).findFirst().orElseThrow();

        assertThat(u.alertMin()).isEqualTo(57.0);
        assertThat(u.alertMax()).isEqualTo(88.0); // (84 + 91) / 2 = 87,5 → sin decimales
    }

    @Test
    void filaConColumnasNulasUsaElMedioYFilaConValorLoRespeta() {
        UmbralMetricaEntity e = new UmbralMetricaEntity("humAmb", 62, 84, 52, 91, 42, 96, null, 90.0);
        when(umbrales.findAll()).thenReturn(List.of(e));

        assertThat(service.getAlertPoints().get("humAmb")).containsExactly(57.0, 90.0);
    }

    @Test
    void guardaLosPuntos() {
        service.updateConfiguracion(cfgCon(humAmb(55.0, 89.0)), "Ana");

        ArgumentCaptor<UmbralMetricaEntity> captor = ArgumentCaptor.forClass(UmbralMetricaEntity.class);
        verify(umbrales, atLeastOnce()).save(captor.capture());
        UmbralMetricaEntity guardada = captor.getAllValues().stream()
                .filter(x -> "humAmb".equals(x.getMetricKey())).findFirst().orElseThrow();
        assertThat(guardada.getAlertMin()).isEqualTo(55.0);
        assertThat(guardada.getAlertMax()).isEqualTo(89.0);
    }

    @Test
    void rechazaUnPuntoFueraDeSuFranja() {
        assertThatThrownBy(() -> service.updateConfiguracion(cfgCon(humAmb(50.0, 88.0)), "Ana"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("punto de alerta");
        assertThatThrownBy(() -> service.updateConfiguracion(cfgCon(humAmb(57.0, 83.0)), "Ana"))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void aceptaUnPayloadSinPuntos() {
        service.updateConfiguracion(cfgCon(humAmb(null, null)), "Ana");
    }

    // ------------------------------------------------------------------ alertas

    private static final MetricSpec SPEC = new MetricSpec("humAmb", "Humedad ambiental", "%",
            new Double[]{62.0, 84.0}, new Double[]{52.0, 91.0}, new Double[]{42.0, 96.0}, 0, 70.0, "ambiente", true);

    private static List<Zona> zonaCon(double valor, String status, MetricSpec spec) {
        Metric m = new Metric(spec.key(), spec.label(), spec.unit(), valor, String.valueOf((long) valor), status, "#000", spec);
        LecturaZona lectura = new LecturaZona(List.of(m), null, 1L, "hace 1 min", false);
        return List.of(new Zona("MZ-2", "Macro-zona 2", "", List.of(), 0, 0, 0, 0, lectura, null));
    }

    private static final Map<String, double[]> PUNTOS = Map.of("humAmb", new double[]{57, 88});

    @Test
    void amarilloMasAllaDelPuntoEmiteWarning() {
        List<Alert> alertas = NurseryService.buildMetricAlerts(zonaCon(55, "warning", SPEC), PUNTOS);

        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).level()).isEqualTo("WARNING");
        assertThat(alertas.get(0).sectorId()).isEqualTo("MZ-2");
        assertThat(alertas.get(0).msg()).contains("Humedad ambiental").contains("punto de alerta (57 %)");
    }

    @Test
    void amarilloAntesDelPuntoNoAlerta() {
        assertThat(NurseryService.buildMetricAlerts(zonaCon(60, "warning", SPEC), PUNTOS)).isEmpty();
        assertThat(NurseryService.buildMetricAlerts(zonaCon(86, "warning", SPEC), PUNTOS)).isEmpty();
    }

    @Test
    void rojoEmiteCritical() {
        List<Alert> alertas = NurseryService.buildMetricAlerts(zonaCon(95, "critical", SPEC), PUNTOS);

        assertThat(alertas).singleElement().satisfies(a -> {
            assertThat(a.level()).isEqualTo("CRITICAL");
            assertThat(a.msg()).contains("arriba del crítico (91 %)");
        });
    }

    @Test
    void lasMetricasInformativasNoAlertan() {
        MetricSpec informativa = new MetricSpec("humAmb", "Humedad ambiental", "%",
                SPEC.ideal(), SPEC.warn(), SPEC.crit(), 0, 70.0, "ambiente", false);

        assertThat(NurseryService.buildMetricAlerts(zonaCon(45, "critical", informativa), PUNTOS)).isEmpty();
    }
}
