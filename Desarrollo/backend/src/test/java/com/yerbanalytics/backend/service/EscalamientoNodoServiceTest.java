package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.parametros.SensingParameters;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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

/** Escalamiento por pulsos consecutivos sin respuesta (spec pulso-sensado, design D4). */
@DisplayName("EscalamientoNodoService")
class EscalamientoNodoServiceTest {

    private ZonaRepository zonas;
    private HistorialService historial;
    private ZoneSafeState safeState;
    private EscalamientoNodoService service;
    private ZonaEntity mz3;

    @BeforeEach
    void setUp() {
        zonas = mock(ZonaRepository.class);
        historial = mock(HistorialService.class);
        safeState = mock(ZoneSafeState.class);
        when(safeState.order(anyString())).thenReturn(new ZoneSafeState.Result(3, 0));
        service = new EscalamientoNodoService(zonas, historial, safeState,
                Clock.fixed(Instant.parse("2026-10-14T13:00:00Z"), ZoneOffset.UTC));
        mz3 = new ZonaEntity();
        mz3.setId("MZ-3");
        mz3.setName("Macro-zona 3");
        when(zonas.findById("MZ-3")).thenReturn(Optional.of(mz3));
    }

    private List<NivelAlerta> nivelesRegistrados(int veces) {
        ArgumentCaptor<DetalleAlerta> det = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(historial, times(veces)).registrarAlerta(eq("MZ-3"), eq("Macro-zona 3"),
                eq(EscalamientoNodoService.REGLA), det.capture(), anyLong());
        return det.getAllValues().stream().map(DetalleAlerta::nivel).toList();
    }

    @Test
    @DisplayName("declara el parámetro del reintento con el nodo fuera de servicio, para figurar como su consumidor")
    void declaraElReintentoConElNodoCaido() {
        assertThat(service.name()).isEqualTo("EscalamientoNodo");
        assertThat(service.parametros()).containsExactly(SensingParameters.OFFLINE_RETRY);
    }

    @Test
    void tresPulsosSinRespuestaEscalanWarningWarningCritical() {
        service.registrarSinRespuesta("MZ-3");
        assertThat(mz3.getPulsosSinRespuesta()).isEqualTo(1);
        assertThat(EscalamientoNodoService.fueraDeServicio(mz3)).isFalse();

        service.registrarSinRespuesta("MZ-3");
        assertThat(mz3.getPulsosSinRespuesta()).isEqualTo(2);
        assertThat(EscalamientoNodoService.fueraDeServicio(mz3)).isFalse();

        service.registrarSinRespuesta("MZ-3");
        assertThat(mz3.getPulsosSinRespuesta()).isEqualTo(3);
        assertThat(EscalamientoNodoService.fueraDeServicio(mz3)).isTrue();

        assertThat(nivelesRegistrados(3))
                .containsExactly(NivelAlerta.WARNING, NivelAlerta.WARNING, NivelAlerta.CRITICAL);
        verify(zonas, times(3)).save(mz3);
    }

    @Test
    @DisplayName("S-02: al tercer pulso sin respuesta ordena el estado seguro de la zona, una sola vez")
    void thirdMissedPulseOrdersTheSafeStateOnce() {
        service.registrarSinRespuesta("MZ-3");
        service.registrarSinRespuesta("MZ-3");
        verify(safeState, never()).order(anyString());

        service.registrarSinRespuesta("MZ-3");
        verify(safeState).order("MZ-3");

        service.registrarSinRespuesta("MZ-3");
        service.registrarSinRespuesta("MZ-3");
        verify(safeState, times(1)).order(anyString());
    }

    @Test
    @DisplayName("la alerta CRITICAL deja registrado que se ordenó el estado seguro")
    void criticalAlertMentionsTheSafeState() {
        mz3.setPulsosSinRespuesta(2);

        service.registrarSinRespuesta("MZ-3");

        ArgumentCaptor<DetalleAlerta> det = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(historial).registrarAlerta(eq("MZ-3"), eq("Macro-zona 3"), eq(EscalamientoNodoService.REGLA),
                det.capture(), anyLong());
        assertThat(det.getValue().nivel()).isEqualTo(NivelAlerta.CRITICAL);
        assertThat(det.getValue().texto()).contains("estado seguro").contains("3 órdenes");
    }

    @Test
    @DisplayName("si el estado seguro no pudo ordenarse, la alerta lo dice y el nodo igual queda fuera de servicio")
    void failedSafeStateIsReportedAndDoesNotBreakTheCount() {
        mz3.setPulsosSinRespuesta(2);
        when(safeState.order("MZ-3")).thenThrow(new RuntimeException("base caída"));

        service.registrarSinRespuesta("MZ-3");

        assertThat(EscalamientoNodoService.fueraDeServicio(mz3)).isTrue();
        ArgumentCaptor<DetalleAlerta> det = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(historial).registrarAlerta(eq("MZ-3"), eq("Macro-zona 3"), eq(EscalamientoNodoService.REGLA),
                det.capture(), anyLong());
        assertThat(det.getValue().texto()).contains("no se pudo ordenar el estado seguro");
    }

    @Test
    @DisplayName("cuando el nodo vuelve no se ordena nada: sin acciones retroactivas")
    void nodeBackOrdersNothing() {
        mz3.setPulsosSinRespuesta(3);

        service.reiniciar(mz3);

        verify(safeState, never()).order(anyString());
    }

    @Test
    void fueraDeServicioLosPulsosSiguientesNoRepitenAlertas() {
        mz3.setPulsosSinRespuesta(3);

        service.registrarSinRespuesta("MZ-3");
        service.registrarSinRespuesta("MZ-3");

        assertThat(mz3.getPulsosSinRespuesta()).isEqualTo(5);
        verify(historial, never()).registrarAlerta(anyString(), any(), anyString(), any(), anyLong());
    }

    @Test
    void laCuentaPersistidaSobreviveAUnReinicio() {
        // Lo que dejó la racha antes del reinicio vive en la fila de la zona: un servicio nuevo la retoma.
        mz3.setPulsosSinRespuesta(2);
        EscalamientoNodoService trasReinicio = new EscalamientoNodoService(zonas, historial, safeState, Clock.systemUTC());

        trasReinicio.registrarSinRespuesta("MZ-3");

        assertThat(nivelesRegistrados(1)).containsExactly(NivelAlerta.CRITICAL);
        assertThat(EscalamientoNodoService.fueraDeServicio(mz3)).isTrue();
    }

    @Test
    void unaCuentaNulaSeLeeComoCero() {
        assertThat(EscalamientoNodoService.cuenta(mz3)).isZero();

        service.registrarSinRespuesta("MZ-3");

        assertThat(nivelesRegistrados(1)).containsExactly(NivelAlerta.WARNING);
    }

    @Test
    void unaRespuestaCortaLaRachaYLaFallaSiguienteVuelveAEmpezarPorWarning() {
        mz3.setPulsosSinRespuesta(2);

        service.reiniciar(mz3);
        assertThat(mz3.getPulsosSinRespuesta()).isZero();
        verify(historial, never()).registrarAlerta(anyString(), any(), anyString(), any(), anyLong());

        service.registrarSinRespuesta("MZ-3");
        assertThat(nivelesRegistrados(1)).containsExactly(NivelAlerta.WARNING);
    }

    @Test
    void siEstabaFueraDeServicioLaRespuestaDejaUnaAlertaInfo() {
        mz3.setPulsosSinRespuesta(4);

        service.reiniciar(mz3);

        assertThat(mz3.getPulsosSinRespuesta()).isZero();
        assertThat(nivelesRegistrados(1)).containsExactly(NivelAlerta.INFO);
    }

    @Test
    void sinRachaLaTelemetriaNoRegistraNada() {
        service.reiniciar(mz3);

        assertThat(mz3.getPulsosSinRespuesta()).isNull();
        verify(historial, never()).registrarAlerta(anyString(), any(), anyString(), any(), anyLong());
    }

    @Test
    void unaZonaQueYaNoExisteSeIgnora() {
        when(zonas.findById("MZ-9")).thenReturn(Optional.empty());

        service.registrarSinRespuesta("MZ-9");

        verify(historial, never()).registrarAlerta(anyString(), any(), anyString(), any(), anyLong());
    }
}
