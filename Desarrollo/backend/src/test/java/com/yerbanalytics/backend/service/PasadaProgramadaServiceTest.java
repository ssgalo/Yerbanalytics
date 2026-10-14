package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
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

/** Pasada diaria del riel (spec pasada-programada), con el reloj del vivero inyectado. */
@DisplayName("PasadaProgramadaService")
class PasadaProgramadaServiceTest {

    /** 10:59:30 en el vivero (UTC-3). */
    private static final Instant ANTES = Instant.parse("2026-10-14T13:59:30Z");

    private PasadaRielService pasadas;
    private ConfiguracionService configuracion;
    private HistorialService historial;
    private RelojDePrueba reloj;
    private PasadaProgramadaService service;
    private ConfiguracionOperativaEntity op;

    @BeforeEach
    void setUp() {
        pasadas = mock(PasadaRielService.class);
        configuracion = mock(ConfiguracionService.class);
        historial = mock(HistorialService.class);
        reloj = new RelojDePrueba(ANTES);
        op = new ConfiguracionOperativaEntity();
        op.setPasadaDiariaActiva(true);
        op.setPasadaDiariaHora("11:00");
        when(configuracion.getConfiguracionOperativa()).thenReturn(op);
        service = new PasadaProgramadaService(pasadas, configuracion, historial, reloj);
    }

    private void avanzar(int segundos) {
        reloj.avanzar(Duration.ofSeconds(segundos));
    }

    @Test
    void disparaLaPasadaEnElMinutoConfigurado() {
        service.revisar();
        verify(pasadas, never()).iniciar();

        avanzar(30);   // 11:00:00
        service.revisar();

        verify(pasadas).iniciar();
    }

    @Test
    void noRepiteDentroDelMismoMinuto() {
        avanzar(31);
        service.revisar();
        avanzar(20);
        service.revisar();
        avanzar(5);
        service.revisar();

        verify(pasadas, times(1)).iniciar();
    }

    @Test
    void desactivadaNoDispara() {
        op.setPasadaDiariaActiva(false);

        avanzar(30);
        service.revisar();

        verify(pasadas, never()).iniciar();
    }

    @Test
    void sinValorGuardadoEstaDesactivada() {
        op.setPasadaDiariaActiva(null);

        avanzar(30);
        service.revisar();

        verify(pasadas, never()).iniciar();
    }

    @Test
    void unCambioDeHoraRigeSinReiniciar() {
        op.setPasadaDiariaHora("11:05");

        avanzar(30);   // 11:00: ya no es la hora
        service.revisar();
        verify(pasadas, never()).iniciar();

        avanzar(5 * 60);   // 11:05
        service.revisar();
        verify(pasadas).iniciar();
    }

    @Test
    void siCambiaLaHoraDespuesDeCorrerVuelveACorrerALaNueva() {
        avanzar(30);   // 11:00
        service.revisar();

        op.setPasadaDiariaHora("11:30");
        avanzar(30 * 60);   // 11:30
        service.revisar();

        verify(pasadas, times(2)).iniciar();
    }

    @Test
    void unRechazoDejaUnaAlertaWarningDeLaZonaDelRecorridoYNoSeReintenta() {
        when(pasadas.iniciar()).thenThrow(
                new PasadaRechazadaException("No hay ningún dispositivo de captura conectado."));
        ZonaEntity mz1 = new ZonaEntity();
        mz1.setId("MZ-1");
        mz1.setName("Macro-zona 1");
        when(pasadas.zonaDelRecorrido()).thenReturn(Optional.of(mz1));

        avanzar(30);
        service.revisar();
        avanzar(20);
        service.revisar();

        ArgumentCaptor<DetalleAlerta> det = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(historial).registrarAlerta(eq("MZ-1"), eq("Macro-zona 1"), eq(PasadaProgramadaService.REGLA),
                det.capture(), anyLong());
        assertThat(det.getValue().nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(det.getValue().texto()).isEqualTo(
                "La pasada programada no pudo iniciarse: No hay ningún dispositivo de captura conectado.");
        verify(pasadas, times(1)).iniciar();
    }

    @Test
    void conUnaPasadaManualEnCursoLaProgramadaSeRechaza() {
        when(pasadas.iniciar()).thenThrow(new PasadaRechazadaException("Ya hay una pasada en curso."));
        when(pasadas.zonaDelRecorrido()).thenReturn(Optional.empty());

        avanzar(30);
        service.revisar();

        ArgumentCaptor<DetalleAlerta> det = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(historial).registrarAlerta(anyString(), any(), eq(PasadaProgramadaService.REGLA), det.capture(),
                anyLong());
        assertThat(det.getValue().texto()).endsWith("Ya hay una pasada en curso.");
    }

    @Test
    void elTickNoDejaEscaparFallas() {
        when(configuracion.getConfiguracionOperativa()).thenThrow(new RuntimeException("base caída"));

        service.tick();

        verify(pasadas, never()).iniciar();
    }
}
