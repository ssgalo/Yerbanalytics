package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
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
    private EscalamientoNodoService service;
    private ZonaEntity mz3;

    @BeforeEach
    void setUp() {
        zonas = mock(ZonaRepository.class);
        historial = mock(HistorialService.class);
        service = new EscalamientoNodoService(zonas, historial,
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
        EscalamientoNodoService trasReinicio = new EscalamientoNodoService(zonas, historial, Clock.systemUTC());

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
