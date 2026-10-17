package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametros;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.parametros.SensingParameters;
import com.yerbanalytics.backend.engine.parametros.ValorParametro;
import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher.Resultado;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Reintento periódico de lectura con el nodo fuera de servicio (reglas_v2 S-02: "cada 15 min"). */
@DisplayName("EscalamientoNodoService - reintento con el nodo fuera de servicio")
class EscalamientoNodoReintentoTest {

    private static final Instant T = Instant.parse("2026-10-14T13:00:00Z");

    private ZonaRepository zones;
    private ComandoZonaPublisher publisher;
    private CatalogoParametrosService parameters;
    private RuleOverridesStore overrides;
    private RelojDePrueba clock;
    private EscalamientoNodoService service;
    private ZonaEntity mz2;

    @BeforeEach
    void setUp() {
        zones = mock(ZonaRepository.class);
        publisher = mock(ComandoZonaPublisher.class);
        parameters = mock(CatalogoParametrosService.class);
        overrides = new RuleOverridesStore();
        clock = new RelojDePrueba(T);
        when(parameters.vigentes()).thenReturn(new CatalogoParametros(List.of()).fabricas());
        when(publisher.leerAhora(anyString())).thenReturn(new Resultado(true, "c", null));
        mz2 = new ZonaEntity();
        mz2.setId("MZ-2");
        mz2.setName("Macro-zona 2");
        mz2.setPulsosSinRespuesta(3);
        when(zones.findByPulsosSinRespuestaGreaterThanEqual(EscalamientoNodoService.FUERA_DE_SERVICIO))
                .thenReturn(List.of(mz2));
        when(zones.findById("MZ-2")).thenReturn(Optional.of(mz2));
        service = new EscalamientoNodoService(zones, mock(HistorialService.class), mock(ZoneSafeState.class),
                publisher, parameters, overrides, clock);
    }

    private void advanceMinutes(long minutes) {
        clock.avanzar(Duration.ofMinutes(minutes));
    }

    private void offlineRetryEvery(double minutes) {
        Map<String, ValorParametro> values = new HashMap<>(new CatalogoParametros(List.of()).fabricas().valores());
        values.put(SensingParameters.OFFLINE_RETRY.clave(), new ValorParametro.Numero(minutes));
        when(parameters.vigentes()).thenReturn(ParametrosVigentes.de(values));
    }

    @Test
    @DisplayName("una zona fuera de servicio recibe LEER_AHORA cada 15 minutos")
    void asksTheOfflineZoneEveryFifteenMinutes() {
        service.retryOfflineNodes();                       // primera vez que la ve: pide
        verify(publisher, times(1)).leerAhora("MZ-2");

        advanceMinutes(14);
        service.retryOfflineNodes();
        verify(publisher, times(1)).leerAhora("MZ-2");

        advanceMinutes(1);
        service.retryOfflineNodes();                       // 15 min desde el último pedido
        verify(publisher, times(2)).leerAhora("MZ-2");

        advanceMinutes(15);
        service.retryOfflineNodes();
        verify(publisher, times(3)).leerAhora("MZ-2");
    }

    @Test
    @DisplayName("sólo mira las zonas con la cuenta de fuera de servicio: una zona sana no se toca")
    void healthyZonesAreNotTouched() {
        when(zones.findByPulsosSinRespuestaGreaterThanEqual(EscalamientoNodoService.FUERA_DE_SERVICIO))
                .thenReturn(List.of());

        service.retryOfflineNodes();

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("al caer el nodo el plazo de 15 minutos arranca desde el último pedido del pulso")
    void theWaitStartsWhenTheNodeFallsOut() {
        mz2.setPulsosSinRespuesta(2);
        service.registrarSinRespuesta("MZ-2");             // el tercero: cruza el umbral
        mz2.setPulsosSinRespuesta(3);

        service.retryOfflineNodes();
        verify(publisher, never()).leerAhora("MZ-2");

        advanceMinutes(15);
        service.retryOfflineNodes();
        verify(publisher, times(1)).leerAhora("MZ-2");
    }

    @Test
    @DisplayName("sensado.reintento-fuera-servicio cambia el intervalo")
    void theIntervalComesFromTheCatalog() {
        offlineRetryEvery(30);
        service.retryOfflineNodes();
        clearInvocations(publisher);

        advanceMinutes(29);
        service.retryOfflineNodes();
        verify(publisher, never()).leerAhora("MZ-2");

        advanceMinutes(1);
        service.retryOfflineNodes();
        verify(publisher, times(1)).leerAhora("MZ-2");
    }

    @Test
    @DisplayName("el override retry-offline reemplaza el intervalo; en 0 pide en cada tick")
    void theSimulatorCanShortenTheWait() {
        overrides.put("EscalamientoNodo", new RuleOverride(true, Map.of("retry-offline", 0.0), java.util.Set.of()));

        service.retryOfflineNodes();
        service.retryOfflineNodes();
        service.retryOfflineNodes();

        verify(publisher, times(3)).leerAhora("MZ-2");
    }

    @Test
    @DisplayName("el override retry-offline también puede alargarlo")
    void theOverrideCanAlsoBeLonger() {
        overrides.put("EscalamientoNodo", new RuleOverride(true, Map.of("retry-offline", 2.0), java.util.Set.of()));
        service.retryOfflineNodes();
        clearInvocations(publisher);

        advanceMinutes(1);
        service.retryOfflineNodes();
        verify(publisher, never()).leerAhora("MZ-2");

        advanceMinutes(1);
        service.retryOfflineNodes();
        verify(publisher, times(1)).leerAhora("MZ-2");
    }

    @Test
    @DisplayName("una zona que vuelve y cae de nuevo arranca su plazo de cero")
    void aZoneThatComesBackStartsFresh() {
        service.retryOfflineNodes();
        clearInvocations(publisher);
        when(zones.findByPulsosSinRespuestaGreaterThanEqual(EscalamientoNodoService.FUERA_DE_SERVICIO))
                .thenReturn(List.of());
        service.retryOfflineNodes();                       // volvió: se olvida su plazo
        when(zones.findByPulsosSinRespuestaGreaterThanEqual(EscalamientoNodoService.FUERA_DE_SERVICIO))
                .thenReturn(List.of(mz2));

        service.retryOfflineNodes();                       // volvió a caer: pide ya, no espera 15 min

        verify(publisher, times(1)).leerAhora("MZ-2");
    }

    @Test
    @DisplayName("si no se puede publicar no escapa nada y se reintenta recién al próximo plazo")
    void publishFailuresStayInside() {
        when(publisher.leerAhora("MZ-2")).thenThrow(new RuntimeException("broker caído"));

        assertThatCode(service::retryOfflineNodes).doesNotThrowAnyException();
        assertThatCode(service::retryOfflineNodes).doesNotThrowAnyException();

        verify(publisher, times(1)).leerAhora("MZ-2");
    }
}
