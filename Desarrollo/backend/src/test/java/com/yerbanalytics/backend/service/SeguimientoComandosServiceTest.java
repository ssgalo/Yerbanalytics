package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.AckActuador;
import com.yerbanalytics.backend.mqtt.NodeError;
import com.yerbanalytics.backend.mqtt.NodeErrorType;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.util.Map;
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

/** Acks de riego, insumo y mediasombra: qué cierra el seguimiento, qué alerta y qué no. */
@DisplayName("SeguimientoComandosService")
class SeguimientoComandosServiceTest {

    private static final long MARGEN_MS = 90_000L;
    private static final long T0 = 1_800_000_000_000L;

    private final HistorialService historial = mock(HistorialService.class);
    private final ZonaRepository zonas = mock(ZonaRepository.class);
    private final Clock reloj = mock(Clock.class);
    private final HardwareFaultService fallas = mock(HardwareFaultService.class);
    private SeguimientoComandosService seguimiento;

    @BeforeEach
    void setUp() {
        when(reloj.millis()).thenReturn(T0);
        ZonaEntity zona = new ZonaEntity();
        zona.setId("MZ-1");
        zona.setName("Macro-zona 1");
        when(zonas.findById("MZ-1")).thenReturn(Optional.of(zona));
        // Por defecto el receptor de errores no se hace cargo (apagado): el ack alerta por su cuenta, como antes de S-05.
        when(fallas.report(any())).thenReturn(HardwareFaultService.Outcome.IGNORED);
        seguimiento = new SeguimientoComandosService(historial, zonas, fallas, reloj, MARGEN_MS);
    }

    private static AckActuador ack(String zonaId, String sectorId, String commandId, String status, String tipo) {
        return new AckActuador(commandId, status,
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("tipo", tipo), zonaId, sectorId);
    }

    private DetalleAlerta alertaRegistrada(String regla) {
        ArgumentCaptor<DetalleAlerta> detalle = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(historial).registrarAlerta(eq("MZ-1"), eq("Macro-zona 1"), eq(regla), detalle.capture(), anyLong());
        return detalle.getValue();
    }

    private void sinAlertas() {
        verify(historial, never()).registrarAlerta(anyString(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void unAckSuccessCierraElSeguimientoSinAlertar() {
        seguimiento.registrarEnvio("c-1", "MZ-1", "MZ-1-001", "valve", Map.of("durationSec", 60));

        seguimiento.registrarAck(ack("MZ-1", "MZ-1-001", "c-1", "SUCCESS", "ok"));

        assertThat(seguimiento.pendientes()).isZero();
        when(reloj.millis()).thenReturn(T0 + 10 * MARGEN_MS);
        seguimiento.revisarVencidos();
        sinAlertas();
    }

    @Test
    void unaFallaMecanicaDeLaMediasombraEsUnaAlertaCritica() {
        seguimiento.registrarEnvio("c-2", "MZ-1", "MZ-1-002", "shade", Map.of("targetPct", 100));

        seguimiento.registrarAck(ack("MZ-1", "MZ-1-002", "c-2", "ERROR", "falla_mecanica"));

        DetalleAlerta d = alertaRegistrada(SeguimientoComandosService.REGLA_FALLA);
        assertThat(d.nivel()).isEqualTo(NivelAlerta.CRITICAL);
        assertThat(d.texto()).contains("MZ-1-002").contains("mediasombra");
        assertThat(seguimiento.pendientes()).isZero();
    }

    @Test
    void laFallaMecanicaDelAckMarcaLaMallaYDejaQueLaAlertaSeaDelServicioDeFallas() {
        when(fallas.report(any())).thenReturn(HardwareFaultService.Outcome.REGISTERED);
        seguimiento.registrarEnvio("c-2b", "MZ-1", "MZ-1-002", "shade", Map.of("targetPct", 100));

        seguimiento.registrarAck(ack("MZ-1", "MZ-1-002", "c-2b", "ERROR", "falla_mecanica"));

        ArgumentCaptor<NodeError> error = ArgumentCaptor.forClass(NodeError.class);
        verify(fallas).report(error.capture());
        assertThat(error.getValue().zoneId()).isEqualTo("MZ-1");
        assertThat(error.getValue().type()).isEqualTo(NodeErrorType.SHADE_LIMIT_SWITCH);
        assertThat(error.getValue().value()).as("el ack no dice qué final de carrera falló").isNull();
        sinAlertas();   // una sola alerta: la que deja el servicio de fallas
        assertThat(seguimiento.pendientes()).isZero();
    }

    @Test
    void laFallaMecanicaYaMarcadaPorElTopicoNoAlertaOtraVez() {
        when(fallas.report(any())).thenReturn(HardwareFaultService.Outcome.DUPLICATE);

        seguimiento.registrarAck(ack("MZ-1", "MZ-1-002", "c-2c", "ERROR", "falla_mecanica"));

        sinAlertas();
    }

    @Test
    void unComandoRechazadoNoMarcaNingunaFalla() {
        seguimiento.registrarAck(ack("MZ-1", "MZ-1-001", "c-3b", "ERROR", "comando_invalido"));
        seguimiento.registrarAck(ack("MZ-1", "MZ-1-001", "c-3c", "SUCCESS", "ok"));
        seguimiento.registrarAck(ack("MZ-1", "MZ-1-001", "c-3d", "ERROR", "cancelado"));

        verify(fallas, never()).report(any());
    }

    @Test
    void unComandoRechazadoPorElNodoEsUnaAdvertencia() {
        seguimiento.registrarEnvio("c-3", "MZ-1", "MZ-1-001", "pump", Map.of());

        seguimiento.registrarAck(ack("MZ-1", "MZ-1-001", "c-3", "ERROR", "comando_invalido"));

        DetalleAlerta d = alertaRegistrada(SeguimientoComandosService.REGLA_FALLA);
        assertThat(d.nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(d.texto()).contains("insumo").contains("comando_invalido");
    }

    @Test
    void reemplazadoYCanceladoNoSonFallas() {
        seguimiento.registrarEnvio("c-4", "MZ-1", "MZ-1-001", "valve", Map.of("durationSec", 60));
        seguimiento.registrarEnvio("c-5", "MZ-1", "MZ-1-001", "pump", Map.of());

        seguimiento.registrarAck(ack("MZ-1", "MZ-1-001", "c-4", "ERROR", "reemplazado"));
        seguimiento.registrarAck(ack("MZ-1", "MZ-1-001", "c-5", "ERROR", "cancelado"));

        sinAlertas();
        assertThat(seguimiento.pendientes()).isZero();
    }

    @Test
    void sinAckEnElPlazoQuedaLaAlertaDeSinRespuesta() {
        seguimiento.registrarEnvio("c-6", "MZ-1", "MZ-1-001", "valve", Map.of("durationSec", 600));

        // El plazo es la duración pedida más el margen: el nodo manda el ack al terminar.
        when(reloj.millis()).thenReturn(T0 + 600_000L + MARGEN_MS - 1);
        seguimiento.revisarVencidos();
        sinAlertas();

        when(reloj.millis()).thenReturn(T0 + 600_000L + MARGEN_MS);
        seguimiento.revisarVencidos();
        DetalleAlerta d = alertaRegistrada(SeguimientoComandosService.REGLA_SIN_RESPUESTA);
        assertThat(d.nivel()).isEqualTo(NivelAlerta.WARNING);
        assertThat(d.texto()).contains("MZ-1-001").contains("riego");
        assertThat(seguimiento.pendientes()).isZero();
    }

    @Test
    void unAckTardioNoVuelveAAlertar() {
        seguimiento.registrarEnvio("c-7", "MZ-1", "MZ-1-002", "shade", Map.of("targetPct", 0));
        when(reloj.millis()).thenReturn(T0 + MARGEN_MS);
        seguimiento.revisarVencidos();

        seguimiento.registrarAck(ack("MZ-1", "MZ-1-002", "c-7", "ERROR", "falla_mecanica"));

        verify(historial, times(1)).registrarAlerta(anyString(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void elAckRepetidoDeUnComandoDuplicadoSeProcesaUnaSolaVez() {
        seguimiento.registrarEnvio("c-8", "MZ-1", "MZ-1-001", "pump", Map.of());

        seguimiento.registrarAck(ack("MZ-1", "MZ-1-001", "c-8", "ERROR", "accion_invalida"));
        seguimiento.registrarAck(ack("MZ-1", "MZ-1-001", "c-8", "ERROR", "accion_invalida"));

        verify(historial, times(1)).registrarAlerta(anyString(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void unaFallaDeUnComandoDesconocidoIgualAlerta() {
        // P. ej. tras un reinicio del backend: lo pendiente se perdió pero la falla es real.
        seguimiento.registrarAck(ack("MZ-1", "MZ-1-001", "perdido", "ERROR", "falla_mecanica"));

        assertThat(alertaRegistrada(SeguimientoComandosService.REGLA_FALLA).nivel()).isEqualTo(NivelAlerta.CRITICAL);
    }

    @Test
    void sinLaZonaEnLaBaseLaAlertaUsaElIdDeLaZona() {
        seguimiento.registrarEnvio("c-9", "MZ-9", "MZ-9-001", "valve", Map.of("durationSec", 10));
        when(reloj.millis()).thenReturn(T0 + 10_000L + MARGEN_MS);

        seguimiento.revisarVencidos();

        verify(historial).registrarAlerta(eq("MZ-9"), eq("MZ-9"), eq(SeguimientoComandosService.REGLA_SIN_RESPUESTA),
                any(DetalleAlerta.class), anyLong());
    }
}
