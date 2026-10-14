package com.yerbanalytics.backend.sensing;

import com.yerbanalytics.backend.config.PulsoProperties;
import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.OrigenPulso;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.PulsoEnCursoException;
import com.yerbanalytics.backend.service.ConfiguracionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Planificador del pulso: un pulso con motor al comienzo de cada franja anclada a las 02:00. */
@DisplayName("SensingScheduler - franjas del pulso")
class SensingSchedulerTest {

    private RelojDePrueba reloj;
    private PulsoSensadoService pulsos;
    private ConfiguracionService configuracion;
    private TaskScheduler taskScheduler;
    private SensingScheduler scheduler;

    private static Instant local(String iso) {
        return LocalDateTime.parse(iso).atZone(ZonaHorariaVivero.ZONA).toInstant();
    }

    @BeforeEach
    void setUp() {
        reloj = new RelojDePrueba(local("2026-10-03T10:00:00"));
        pulsos = mock(PulsoSensadoService.class);
        configuracion = mock(ConfiguracionService.class);
        taskScheduler = mock(TaskScheduler.class);
        intervalo(240);
        scheduler = new SensingScheduler(pulsos, configuracion, taskScheduler, new PulsoProperties(), reloj);
    }

    private void intervalo(Integer minutos) {
        ConfiguracionOperativaEntity op = new ConfiguracionOperativaEntity();
        op.setIntervaloSensadoMinutos(minutos);
        when(configuracion.getConfiguracionOperativa()).thenReturn(op);
    }

    private void avanzarHasta(String iso) {
        reloj.avanzar(Duration.between(reloj.instant(), local(iso)));
    }

    @Test
    @DisplayName("al arrancar, la franja vigente se da por atendida: no hay pulso de recuperación")
    void alArrancarNoDispara() {
        scheduler.start();
        scheduler.revisarFranja();

        verify(pulsos, never()).iniciar(any(), anyBoolean());
        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    }

    @Test
    @DisplayName("una franja nueva dispara un pulso programado con motor, una sola vez")
    void franjaNueva_disparaUnaVez() {
        scheduler.start();
        avanzarHasta("2026-10-03T13:59:59");
        scheduler.revisarFranja();
        verify(pulsos, never()).iniciar(any(), anyBoolean());

        avanzarHasta("2026-10-03T14:00:00");
        scheduler.revisarFranja();
        scheduler.revisarFranja();
        avanzarHasta("2026-10-03T15:30:00");
        scheduler.revisarFranja();

        verify(pulsos, times(1)).iniciar(OrigenPulso.PROGRAMADO, true);
    }

    @Test
    @DisplayName("se guarda 120 a las 10:30 con el último pulso a las 10:00: el próximo es a las 12:00")
    void cambioDeIntervaloEnCaliente() {
        scheduler.start();
        avanzarHasta("2026-10-03T10:30:00");
        intervalo(120);
        scheduler.revisarFranja();
        verify(pulsos, never()).iniciar(any(), anyBoolean());

        avanzarHasta("2026-10-03T11:59:59");
        scheduler.revisarFranja();
        verify(pulsos, never()).iniciar(any(), anyBoolean());

        avanzarHasta("2026-10-03T12:00:00");
        scheduler.revisarFranja();
        verify(pulsos, times(1)).iniciar(OrigenPulso.PROGRAMADO, true);
    }

    @Test
    @DisplayName("si hay un pulso en curso al comenzar la franja, el programado se omite sin romper")
    void pulsoEnCurso_seOmite() {
        scheduler.start();
        when(pulsos.iniciar(OrigenPulso.PROGRAMADO, true)).thenThrow(new PulsoEnCursoException("en curso"));
        avanzarHasta("2026-10-03T14:00:00");

        assertThatCode(scheduler::revisarFranja).doesNotThrowAnyException();
        scheduler.revisarFranja();

        verify(pulsos, times(1)).iniciar(OrigenPulso.PROGRAMADO, true);
    }

    @Test
    @DisplayName("el tick aísla las fallas: si la franja falla, igual avanza los reintentos")
    void tickAislaFallas() {
        scheduler.start();
        avanzarHasta("2026-10-03T14:00:00");
        when(pulsos.iniciar(any(), anyBoolean())).thenThrow(new IllegalStateException("db caída"));
        doThrow(new IllegalStateException("broker caído")).when(pulsos).revisarReintentos();

        assertThatCode(scheduler::tick).doesNotThrowAnyException();

        verify(pulsos).revisarReintentos();
    }

    @Test
    @DisplayName("sin poder leer la configuración se usan 240 min")
    void errorDeConfiguracion_usa240() {
        scheduler.start();
        when(configuracion.getConfiguracionOperativa()).thenThrow(new IllegalStateException("db caída"));
        avanzarHasta("2026-10-03T13:00:00");
        scheduler.revisarFranja();
        verify(pulsos, never()).iniciar(any(), anyBoolean());

        avanzarHasta("2026-10-03T14:00:00");
        scheduler.revisarFranja();
        verify(pulsos).iniciar(OrigenPulso.PROGRAMADO, true);
    }
}
