package com.yerbanalytics.backend.sensing;

import com.yerbanalytics.backend.config.PulsoProperties;
import com.yerbanalytics.backend.engine.riego.RelojDePrueba;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher.Resultado;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.OrigenPulso;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.Pulso;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.PulsoEnCursoException;
import com.yerbanalytics.backend.service.EscalamientoNodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pulso de sensado: pedido a todas las zonas, correlación por ventana, reintentos y cierre (spec pulso-sensado). */
@DisplayName("PulsoSensadoService")
class PulsoSensadoServiceTest {

    private static final Instant T = Instant.parse("2026-10-14T13:00:00Z");   // 10:00 en el vivero

    private ZonaRepository zonas;
    private ComandoZonaPublisher publisher;
    private EscalamientoNodoService escalamiento;
    private RelojDePrueba reloj;
    private PulsoSensadoService service;

    @BeforeEach
    void setUp() {
        zonas = mock(ZonaRepository.class);
        publisher = mock(ComandoZonaPublisher.class);
        escalamiento = mock(EscalamientoNodoService.class);
        reloj = new RelojDePrueba(T);
        when(zonas.findAllIds()).thenReturn(List.of("MZ-1", "MZ-2", "MZ-3"));
        when(publisher.leerAhora(anyString())).thenReturn(new Resultado(true, "c", null));
        service = new PulsoSensadoService(zonas, publisher, escalamiento, new PulsoProperties(), reloj);
    }

    private long ms(int segundosDesdeT) {
        return T.plusSeconds(segundosDesdeT).toEpochMilli();
    }

    private void avanzar(int segundos) {
        reloj.avanzar(Duration.ofSeconds(segundos));
    }

    // --- iniciar ---

    @Test
    void unPulsoPideUnaLecturaACadaZona() {
        Pulso p = service.iniciar(OrigenPulso.MANUAL, false);

        verify(publisher).leerAhora("MZ-1");
        verify(publisher).leerAhora("MZ-2");
        verify(publisher).leerAhora("MZ-3");
        assertThat(p.origen()).isEqualTo(OrigenPulso.MANUAL);
        assertThat(p.ejecutaMotor()).isFalse();
        assertThat(p.pedidoEn()).isEqualTo(T.toEpochMilli());
        assertThat(service.enCurso()).isTrue();
    }

    @Test
    void conUnPulsoEnCursoElSiguienteSeRechazaSinPublicar() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);
        clearInvocations(publisher);

        assertThatThrownBy(() -> service.iniciar(OrigenPulso.MANUAL, true))
                .isInstanceOf(PulsoEnCursoException.class)
                .hasMessageContaining("en curso");
        verifyNoInteractions(publisher);
    }

    @Test
    void topologiaVaciaNoPublicaYElPulsoTerminaEnElActo() {
        when(zonas.findAllIds()).thenReturn(List.of());

        service.iniciar(OrigenPulso.PROGRAMADO, true);

        verifyNoInteractions(publisher);
        assertThat(service.enCurso()).isFalse();
    }

    @Test
    void unaFallaAlPublicarAUnaZonaNoFrenaALasDemas() {
        doThrow(new RuntimeException("broker caído")).when(publisher).leerAhora("MZ-1");

        service.iniciar(OrigenPulso.MANUAL, true);

        verify(publisher).leerAhora("MZ-2");
        verify(publisher).leerAhora("MZ-3");
        assertThat(service.enCurso()).isTrue();
    }

    // --- respondePulso ---

    @Test
    void laPrimeraRespuestaDeUnPulsoConMotorEjecutaElMotor() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);

        assertThat(service.respondePulso("MZ-1", ms(2))).isTrue();
    }

    @Test
    void unPulsoSinMotorNoEjecutaElMotor() {
        service.iniciar(OrigenPulso.MANUAL, false);

        assertThat(service.respondePulso("MZ-1", ms(2))).isFalse();
    }

    @Test
    void laSegundaRespuestaDeLaMismaZonaNoVuelveAEvaluar() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);
        service.respondePulso("MZ-1", ms(2));

        assertThat(service.respondePulso("MZ-1", ms(5))).isFalse();
    }

    @Test
    void unaTelemetriaAnteriorAlPedidoNoCuentaComoRespuesta() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);

        assertThat(service.respondePulso("MZ-1", ms(-5))).isFalse();
        assertThat(service.respondePulso("MZ-1", ms(1))).isTrue();
    }

    @Test
    void fueraDePulsoNingunaTelemetriaEjecutaElMotor() {
        assertThat(service.respondePulso("MZ-1", ms(0))).isFalse();
    }

    // --- recordResponse (la respuesta completa: si respondió y si ejecuta el motor) ---

    @Test
    @DisplayName("un pulso con motor: la primera respuesta responde y ejecuta el motor")
    void recordResponse_withEngine() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);

        var r = service.recordResponse("MZ-1", ms(2));

        assertThat(r.responded()).isTrue();
        assertThat(r.runsEngine()).isTrue();
    }

    @Test
    @DisplayName("un pulso sin motor: la primera respuesta responde pero NO ejecuta el motor")
    void recordResponse_withoutEngineStillAnswersThePulse() {
        service.iniciar(OrigenPulso.MANUAL, false);

        var r = service.recordResponse("MZ-1", ms(2));

        assertThat(r.responded()).isTrue();
        assertThat(r.runsEngine()).isFalse();
    }

    @Test
    @DisplayName("fuera de un pulso, la second respuesta de la zona o una anterior al pedido no responden")
    void recordResponse_nothingToAnswer() {
        assertThat(service.recordResponse("MZ-1", ms(0)).responded()).isFalse();

        service.iniciar(OrigenPulso.PROGRAMADO, true);
        assertThat(service.recordResponse("MZ-1", ms(-5)).responded()).isFalse();
        assertThat(service.recordResponse("MZ-1", ms(1)).responded()).isTrue();
        var second = service.recordResponse("MZ-1", ms(5));
        assertThat(second.responded()).isFalse();
        assertThat(second.runsEngine()).isFalse();
    }

    @Test
    @DisplayName("recordResponse y respondePulso comparten la marca: la zona responde una sola vez")
    void recordResponse_andRespondePulsoShareTheMark() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);

        assertThat(service.respondePulso("MZ-1", ms(1))).isTrue();
        assertThat(service.recordResponse("MZ-1", ms(2)).responded()).isFalse();
    }

    @Test
    void elPulsoTerminaCuandoRespondenTodasLasZonas() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);
        service.respondePulso("MZ-1", ms(1));
        service.respondePulso("MZ-2", ms(1));
        assertThat(service.enCurso()).isTrue();

        service.respondePulso("MZ-3", ms(1));

        assertThat(service.enCurso()).isFalse();
        // Terminado, se puede pedir otro.
        service.iniciar(OrigenPulso.MANUAL, false);
    }

    @Test
    void dosTelemetriasSimultaneasDeLaMismaZonaEvaluanUnaSolaVez() throws Exception {
        service.iniciar(OrigenPulso.PROGRAMADO, true);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Boolean>> resultados = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                resultados.add(pool.submit(() -> {
                    largada.await();
                    return service.respondePulso("MZ-1", ms(1));
                }));
            }
            largada.countDown();
            int evaluaciones = 0;
            for (Future<Boolean> f : resultados) {
                if (f.get(5, TimeUnit.SECONDS)) {
                    evaluaciones++;
                }
            }
            assertThat(evaluaciones).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- reintentos ---

    @Test
    void antesDelPlazoNoSeReenviaNada() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);
        clearInvocations(publisher);

        avanzar(59);
        service.revisarReintentos();

        verifyNoInteractions(publisher);
    }

    @Test
    void aLosSesentaSegundosSeReenviaSoloALasPendientes() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);
        service.respondePulso("MZ-1", ms(2));
        clearInvocations(publisher);

        avanzar(60);
        service.revisarReintentos();

        verify(publisher, never()).leerAhora("MZ-1");
        verify(publisher).leerAhora("MZ-2");
        verify(publisher).leerAhora("MZ-3");
    }

    @Test
    void unaRespuestaTrasUnReintentoNoCuentaComoFalla() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);
        avanzar(60);
        service.revisarReintentos();

        assertThat(service.respondePulso("MZ-2", ms(63))).isTrue();
        avanzar(200);
        service.revisarReintentos();

        verify(escalamiento, never()).registrarSinRespuesta("MZ-2");
    }

    @Test
    void alVencerElTercerPedidoLaZonaQuedaSinRespuestaYElPulsoCierra() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);
        service.respondePulso("MZ-1", ms(1));
        service.respondePulso("MZ-2", ms(1));
        clearInvocations(publisher);

        avanzar(60);
        service.revisarReintentos();   // 2.º pedido
        avanzar(60);
        service.revisarReintentos();   // 3.º pedido
        verify(publisher, times(2)).leerAhora("MZ-3");
        verify(escalamiento, never()).registrarSinRespuesta(anyString());
        assertThat(service.enCurso()).isTrue();

        avanzar(60);                   // T + 180 s: vence el tercero
        service.revisarReintentos();

        verify(publisher, times(2)).leerAhora("MZ-3");
        verify(escalamiento).registrarSinRespuesta("MZ-3");
        assertThat(service.enCurso()).isFalse();
        // Una telemetría tardía ya no responde a ese pulso.
        assertThat(service.respondePulso("MZ-3", ms(185))).isFalse();
    }

    @Test
    void unaFallaAlRegistrarElEscalamientoDeUnaZonaNoFrenaALasDemas() {
        service.iniciar(OrigenPulso.PROGRAMADO, true);
        doThrow(new RuntimeException("base caída")).when(escalamiento).registrarSinRespuesta("MZ-1");

        for (int i = 0; i < 3; i++) {
            avanzar(60);
            service.revisarReintentos();
        }

        verify(escalamiento).registrarSinRespuesta("MZ-2");
        verify(escalamiento).registrarSinRespuesta("MZ-3");
        assertThat(service.enCurso()).isFalse();
    }
}
