package com.yerbanalytics.backend.engine.weather;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El aviso del modo sin pronóstico: un INFO al entrar y otro al salir, ninguno repetido. */
@DisplayName("ForecastModeMonitor - aviso de entrada y salida del modo sin pronóstico")
class ForecastModeMonitorTest {

    private static final Instant NOW = Instant.parse("2026-10-22T13:00:00Z");

    private WeatherService weather;
    private HistorialService history;
    private ForecastModeMonitor monitor;

    @BeforeEach
    void setUp() {
        weather = mock(WeatherService.class);
        history = mock(HistorialService.class);
        monitor = new ForecastModeMonitor(weather, history, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void status(ForecastStatus status) {
        when(weather.status()).thenReturn(status);
        monitor.check();
    }

    private List<DetalleAlerta> alerts() {
        ArgumentCaptor<DetalleAlerta> captor = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(history, org.mockito.Mockito.atLeast(0)).registrarAlerta(eq("—"), eq("Sistema"),
                eq(ForecastModeMonitor.RULE_NAME), captor.capture(), eq(NOW.toEpochMilli()));
        return captor.getAllValues();
    }

    @Test
    void quedanExactamenteDosInfoCuandoSeCaeYVuelve() {
        status(ForecastStatus.AVAILABLE);
        status(ForecastStatus.EXPIRED);
        status(ForecastStatus.EXPIRED);
        status(ForecastStatus.ABSENT);
        status(ForecastStatus.AVAILABLE);
        status(ForecastStatus.AVAILABLE);

        List<DetalleAlerta> alerts = alerts();
        assertThat(alerts).hasSize(2);
        assertThat(alerts).allSatisfy(a -> assertThat(a.nivel()).isEqualTo(NivelAlerta.INFO));
        assertThat(alerts.get(0).texto()).contains("Sin pronóstico").contains("supuestos conservadores");
        assertThat(alerts.get(1).texto()).contains("Pronóstico recuperado");
    }

    @Test
    void conPronosticoDesdeElPrincipioNoAvisaNada() {
        status(ForecastStatus.AVAILABLE);
        status(ForecastStatus.AVAILABLE);

        verify(history, never()).registrarAlerta(anyString(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void siArrancaSinPronosticoAvisaUnaVezYAlVolverOtra() {
        status(ForecastStatus.ABSENT);
        status(ForecastStatus.ABSENT);
        status(ForecastStatus.AVAILABLE);

        assertThat(alerts()).hasSize(2);
    }

    @Test
    void cadaCaidaAvisaDeNuevo() {
        status(ForecastStatus.AVAILABLE);
        status(ForecastStatus.ABSENT);
        status(ForecastStatus.AVAILABLE);
        status(ForecastStatus.EXPIRED);

        verify(history, times(3)).registrarAlerta(eq("—"), eq("Sistema"), eq(ForecastModeMonitor.RULE_NAME), any(),
                eq(NOW.toEpochMilli()));
    }

    @Test
    void cadaChequeoPidePronosticoSinEsperarParaQueSigaRefrescando() {
        status(ForecastStatus.AVAILABLE);

        verify(weather).getForecastSinEspera();
    }
}
