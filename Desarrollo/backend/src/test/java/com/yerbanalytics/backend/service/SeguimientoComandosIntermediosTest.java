package com.yerbanalytics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.AckActuador;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ACK intermedios del nodo (add-cola-actuadores-nodo, design D-8): un EN_CURSO mantiene el comando pendiente,
 * recalcula su plazo desde el arranque y publica {@link ActuatorProgress}; sólo un final lo resuelve.
 */
@DisplayName("SeguimientoComandosService - ACK intermedios")
class SeguimientoComandosIntermediosTest {

    private static final long MARGEN_MS = 90_000L;
    private static final long T0 = 1_800_000_000_000L;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HistorialService historial = mock(HistorialService.class);
    private final ZonaRepository zonas = mock(ZonaRepository.class);
    private final Clock reloj = mock(Clock.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final HardwareFaultService fallas = mock(HardwareFaultService.class);
    private SeguimientoComandosService seguimiento;

    @BeforeEach
    void setUp() {
        when(reloj.millis()).thenReturn(T0);
        ZonaEntity zona = new ZonaEntity();
        zona.setId("MZ-1");
        zona.setName("Macro-zona 1");
        when(zonas.findById("MZ-1")).thenReturn(Optional.of(zona));
        when(fallas.report(any())).thenReturn(HardwareFaultService.Outcome.IGNORED);
        seguimiento = new SeguimientoComandosService(historial, zonas, fallas, reloj, MARGEN_MS, eventos);
    }

    private static AckActuador ack(String commandId, String status, ObjectNode detalle) {
        return new AckActuador(commandId, status, detalle, "MZ-1", "MZ-1-001");
    }

    private static AckActuador enCola(String commandId, int posicion, long esperaMaxSec) {
        return ack(commandId, "EN_CURSO", JSON.createObjectNode().put("tipo", "en_cola")
                .put("posicion", posicion).put("esperaMaxSec", esperaMaxSec));
    }

    private static AckActuador iniciado(String commandId) {
        return ack(commandId, "EN_CURSO", JSON.createObjectNode().put("tipo", "iniciado").put("durationSec", 20));
    }

    private static AckActuador finalOk(String commandId) {
        return ack(commandId, "SUCCESS", JSON.createObjectNode().put("tipo", "ok"));
    }

    private void aLas(long offsetMs) {
        when(reloj.millis()).thenReturn(T0 + offsetMs);
    }

    private void sinAlertas() {
        verify(historial, never()).registrarAlerta(anyString(), anyString(), anyString(), any(), anyLong());
    }

    private void unaAlerta() {
        verify(historial, times(1)).registrarAlerta(anyString(), anyString(), anyString(), any(), anyLong());
    }

    private List<Object> eventosPublicados() {
        ArgumentCaptor<Object> c = ArgumentCaptor.forClass(Object.class);
        verify(eventos, atLeastOnce()).publishEvent(c.capture());
        return c.getAllValues();
    }

    @Test
    void unEnCursoNoResuelveYElSuccessPosteriorCierra() {
        seguimiento.registrarEnvio("c-1", "MZ-1", "MZ-1-001", "valve", Map.of("durationSec", 20));

        seguimiento.registrarAck(iniciado("c-1"));
        assertThat(seguimiento.pendientes()).as("el intermedio lo deja pendiente").isEqualTo(1);

        seguimiento.registrarAck(finalOk("c-1"));
        assertThat(seguimiento.pendientes()).as("el final no se ignora como repetido").isZero();
        sinAlertas();
    }

    @Test
    void conEnColaElPlazoSumaLaEsperaMaximaALaDuracion() {
        seguimiento.registrarEnvio("c-2", "MZ-1", "MZ-1-002", "valve", Map.of("durationSec", 20));

        seguimiento.registrarAck(enCola("c-2", 1, 20));

        // vence en t0 + 20 s de espera + 20 s de riego + margen, y no antes
        aLas((20 + 20) * 1000L + MARGEN_MS - 1);
        seguimiento.revisarVencidos();
        sinAlertas();

        aLas((20 + 20) * 1000L + MARGEN_MS);
        seguimiento.revisarVencidos();
        unaAlerta();
    }

    @Test
    void conIniciadoElPlazoSaleDeLaHoraDeRecepcion() {
        seguimiento.registrarEnvio("c-3", "MZ-1", "MZ-1-002", "valve", Map.of("durationSec", 20));
        aLas(30_000L);

        seguimiento.registrarAck(iniciado("c-3"));

        aLas(30_000L + 20_000L + MARGEN_MS - 1);
        seguimiento.revisarVencidos();
        sinAlertas();

        aLas(30_000L + 20_000L + MARGEN_MS);
        seguimiento.revisarVencidos();
        unaAlerta();
    }

    @Test
    void enColaSinEsperaMaximaNoTocaElPlazo() {
        seguimiento.registrarEnvio("c-4", "MZ-1", "MZ-1-002", "valve", Map.of("durationSec", 20));
        ObjectNode sinEspera = JSON.createObjectNode().put("tipo", "en_cola").put("posicion", 1);

        seguimiento.registrarAck(ack("c-4", "EN_CURSO", sinEspera));

        aLas(20_000L + MARGEN_MS);
        seguimiento.revisarVencidos();
        unaAlerta();
    }

    @Test
    void unStatusDesconocidoSeLogueaYNoResuelve() {
        seguimiento.registrarEnvio("c-5", "MZ-1", "MZ-1-001", "valve", Map.of("durationSec", 20));

        seguimiento.registrarAck(ack("c-5", "ALGO_NUEVO", JSON.createObjectNode().put("tipo", "x")));
        assertThat(seguimiento.pendientes()).isEqualTo(1);

        seguimiento.registrarAck(finalOk("c-5"));
        assertThat(seguimiento.pendientes()).isZero();
    }

    @Test
    void unIntermedioDeUnComandoResueltoSeIgnora() {
        seguimiento.registrarEnvio("c-6", "MZ-1", "MZ-1-001", "valve", Map.of("durationSec", 20));
        seguimiento.registrarAck(finalOk("c-6"));

        seguimiento.registrarAck(iniciado("c-6"));

        verify(eventos, never()).publishEvent(any(Object.class));
        assertThat(seguimiento.pendientes()).isZero();
    }

    @Test
    void unIntermedioDeUnComandoDesconocidoSeIgnora() {
        seguimiento.registrarAck(iniciado("fantasma"));

        verify(eventos, never()).publishEvent(any(Object.class));
        sinAlertas();
    }

    @Test
    void publicaElProgresoDeCadaIntermedio() {
        seguimiento.registrarEnvio("c-7", "MZ-1", "MZ-1-001", "valve", Map.of("durationSec", 20));
        aLas(1_000L);
        seguimiento.registrarAck(enCola("c-7", 2, 45));
        aLas(46_000L);
        seguimiento.registrarAck(iniciado("c-7"));

        assertThat(eventosPublicados()).containsExactly(
                new ActuatorProgress("MZ-1", "MZ-1-001", "valve", "c-7", ActuatorProgress.Phase.QUEUED,
                        T0 + 1_000L, 45_000L),
                new ActuatorProgress("MZ-1", "MZ-1-001", "valve", "c-7", ActuatorProgress.Phase.STARTED,
                        T0 + 46_000L, 0L));
    }

    @Test
    void unEnColaDeLaBombaDeInsumoUsaLaDuracionDeLosMl() {
        seguimiento.registrarEnvio("p-1", "MZ-1", "MZ-1-001", "pump", Map.of("ml", 10));

        seguimiento.registrarAck(enCola("p-1", 1, 30));

        aLas((30 + 10) * 1000L + MARGEN_MS - 1);
        seguimiento.revisarVencidos();
        sinAlertas();
        aLas((30 + 10) * 1000L + MARGEN_MS);
        seguimiento.revisarVencidos();
        unaAlerta();
    }

    @Test
    void unEnCursoDeLaMediasombraTambienSeSigue() {
        seguimiento.registrarEnvio("s-1", "MZ-1", "MZ-1-001", "shade", Map.of("targetPct", 0));

        seguimiento.registrarAck(iniciado("s-1"));

        assertThat(seguimiento.pendientes()).isEqualTo(1);
        verify(eventos).publishEvent(any(ActuatorProgress.class));
    }
}
