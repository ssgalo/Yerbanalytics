package com.yerbanalytics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.engine.ComandoActuadorPublisher.Resultado;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.model.DispositivoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.mqtt.MqttCommandGateway;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Estado seguro de una macro-zona (reglas_v2 S-02): riego cerrado, bombas detenidas y mediasombra cerrada. */
@DisplayName("ZoneSafeState")
class ZoneSafeStateTest {

    private SectorRepository sectors;
    private DispositivoRepository devices;
    private ComandoActuadorPublisher publisher;
    private ZoneSafeState safeState;

    private static SectorEntity sector(String id) {
        SectorEntity s = new SectorEntity();
        s.setId(id);
        return s;
    }

    private static DispositivoEntity device(String type, String sectorId) {
        DispositivoEntity d = new DispositivoEntity();
        d.setId(type + "-" + sectorId);
        d.setTipo(type);
        d.setSectorId(sectorId);
        return d;
    }

    @BeforeEach
    void setUp() {
        sectors = mock(SectorRepository.class);
        devices = mock(DispositivoRepository.class);
        publisher = mock(ComandoActuadorPublisher.class);
        when(publisher.publicar(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new Resultado(true, "c", null));
        when(sectors.findByZonaId("MZ-2")).thenReturn(List.of(sector("MZ-2-001"), sector("MZ-2-002")));
        safeState = new ZoneSafeState(sectors, new ActuatorShutdown(devices, publisher));
    }

    private void registered(DispositivoEntity... registered) {
        when(devices.findBySectorIdInAndTipoIn(anyCollection(), anyCollection())).thenReturn(List.of(registered));
    }

    @Test
    @DisplayName("cierra la válvula, detiene la bomba y cierra la mediasombra de cada sector con ese equipo registrado")
    void ordersEveryRegisteredActuator() {
        registered(device(HardwareService.ELECTROVALVULA, "MZ-2-001"),
                device(HardwareService.BOMBA, "MZ-2-001"),
                device(HardwareService.MEDIASOMBRA, "MZ-2-002"));

        ActuatorShutdown.Result result = safeState.order("MZ-2");

        verify(publisher).publicar("MZ-2", "MZ-2-001", "valve", "OFF", Map.of());
        verify(publisher).publicar("MZ-2", "MZ-2-001", "pump", "OFF", Map.of());
        verify(publisher).publicar("MZ-2", "MZ-2-002", "shade", "SET",
                Map.of("targetPct", 0, "prioridad", "INMEDIATA"));
        assertThat(result).isEqualTo(new ActuatorShutdown.Result(3, 0));
    }

    @Test
    @DisplayName("publica los OFF primero y la mediasombra al final, aunque el repositorio la devuelva antes; la malla va con prioridad INMEDIATA")
    void closesWaterFirstAndShadeLastWithPriority() {
        registered(device(HardwareService.MEDIASOMBRA, "MZ-2-002"),
                device(HardwareService.ELECTROVALVULA, "MZ-2-001"),
                device(HardwareService.BOMBA, "MZ-2-001"),
                device(HardwareService.ELECTROVALVULA, "MZ-2-002"));

        safeState.order("MZ-2");

        var orden = org.mockito.Mockito.inOrder(publisher);
        orden.verify(publisher, org.mockito.Mockito.times(3))
                .publicar(anyString(), anyString(), org.mockito.ArgumentMatchers.argThat(a -> !"shade".equals(a)),
                        eq("OFF"), any());
        orden.verify(publisher).publicar("MZ-2", "MZ-2-002", "shade", "SET",
                Map.of(ContratoNodo.PARAM_PRIORIDAD, ContratoNodo.PRIORIDAD_INMEDIATA, "targetPct", 0));
        orden.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("busca los equipos sólo entre los sectores de la zona y sólo de los tres tipos de actuador")
    void looksOnlyAtTheZoneActuators() {
        registered();

        safeState.order("MZ-2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> types = ArgumentCaptor.forClass(Collection.class);
        verify(devices).findBySectorIdInAndTipoIn(ids.capture(), types.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder("MZ-2-001", "MZ-2-002");
        assertThat(types.getValue()).containsExactlyInAnyOrder(
                HardwareService.ELECTROVALVULA, HardwareService.BOMBA, HardwareService.MEDIASOMBRA);
    }

    @Test
    @DisplayName("sin equipos registrados no publica nada")
    void nothingRegisteredNothingPublished() {
        registered();

        ActuatorShutdown.Result result = safeState.order("MZ-2");

        verify(publisher, never()).publicar(anyString(), anyString(), anyString(), anyString(), any());
        assertThat(result).isEqualTo(new ActuatorShutdown.Result(0, 0));
    }

    @Test
    @DisplayName("dos equipos del mismo tipo en un sector reciben una sola orden")
    void duplicatedDevicesGetOneOrder() {
        registered(device(HardwareService.ELECTROVALVULA, "MZ-2-001"),
                device(HardwareService.ELECTROVALVULA, "MZ-2-001"));

        ActuatorShutdown.Result result = safeState.order("MZ-2");

        assertThat(result.commands()).isEqualTo(1);
    }

    @Test
    @DisplayName("una orden que no sale se cuenta como fallida y no frena a las demás")
    void aFailedOrderDoesNotStopTheRest() {
        registered(device(HardwareService.ELECTROVALVULA, "MZ-2-001"),
                device(HardwareService.MEDIASOMBRA, "MZ-2-002"));
        when(publisher.publicar(eq("MZ-2"), eq("MZ-2-001"), eq("valve"), anyString(), any()))
                .thenReturn(new Resultado(false, "c", "broker caído"));
        when(publisher.publicar(eq("MZ-2"), eq("MZ-2-002"), eq("shade"), anyString(), any()))
                .thenThrow(new RuntimeException("explotó"));

        ActuatorShutdown.Result result = safeState.order("MZ-2");

        assertThat(result).isEqualTo(new ActuatorShutdown.Result(0, 2));
    }

    @Test
    @DisplayName("sale por el publicador de actuadores: el seguimiento vigila cada orden hasta su ack")
    void ordersAreWatchedByTheFollowUp() {
        SeguimientoComandosService followUp = new SeguimientoComandosService(
                mock(HistorialService.class), mock(ZonaRepository.class), mock(HardwareFaultService.class),
                Clock.systemUTC(), 90_000L, mock(org.springframework.context.ApplicationEventPublisher.class));
        ComandoActuadorPublisher real = new ComandoActuadorPublisher(
                mock(MqttCommandGateway.class), new ObjectMapper(), followUp);
        ZoneSafeState withFollowUp = new ZoneSafeState(sectors, new ActuatorShutdown(devices, real));
        registered(device(HardwareService.ELECTROVALVULA, "MZ-2-001"),
                device(HardwareService.MEDIASOMBRA, "MZ-2-002"));

        withFollowUp.order("MZ-2");

        assertThat(followUp.pendientes()).isEqualTo(2);
    }
}
