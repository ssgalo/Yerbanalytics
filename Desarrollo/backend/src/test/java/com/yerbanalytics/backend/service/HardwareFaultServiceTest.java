package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.parametros.ParametrosPorPieza;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.TestRuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.exception.HardwareConflictException;
import com.yerbanalytics.backend.exception.InvalidHardwareException;
import com.yerbanalytics.backend.model.DispositivoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.NodeError;
import com.yerbanalytics.backend.mqtt.NodeErrorType;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.HardwareFaultService.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S-05: el servicio que marca un dispositivo como averiado a partir de lo que informa el nodo, deja UNA alerta crítica
 * con el texto de la v2, y levanta la marca al registrarse la reparación.
 */
@DisplayName("HardwareFaultService (S-05)")
class HardwareFaultServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-17T13:05:00Z");

    private DispositivoRepository dispositivos;
    private SectorRepository sectores;
    private ZonaRepository zonas;
    private HistorialService historial;
    private final TestRuleOverrides overrides = new TestRuleOverrides();
    private HardwareFaultService service;
    private ZonaEntity zona;
    private DispositivoEntity valvula;
    private DispositivoEntity malla;

    @BeforeEach
    void setUp() {
        dispositivos = mock(DispositivoRepository.class);
        sectores = mock(SectorRepository.class);
        zonas = mock(ZonaRepository.class);
        historial = mock(HistorialService.class);
        CatalogoParametrosService catalogo = mock(CatalogoParametrosService.class);
        when(catalogo.vigentes()).thenReturn(ParametrosPorPieza.uniforme(ReglaTestSupport.fabrica()));

        zona = new ZonaEntity();
        zona.setId("MZ-1");
        zona.setName("Macro-zona 1");
        zona.setHumSusRaw(40.0);
        when(zonas.findById("MZ-1")).thenReturn(Optional.of(zona));
        when(sectores.findByZonaId("MZ-1")).thenReturn(List.of(sector("MZ-1-011"), sector("MZ-1-012")));

        valvula = dispositivo("DEV-101", HardwareService.ELECTROVALVULA, "MZ-1-012");
        // La mediasombra es de la macro-zona: se registra con la zona, como el nodo testigo.
        malla = dispositivo("DEV-011", HardwareService.MEDIASOMBRA, null);
        malla.setZonaId("MZ-1");
        when(dispositivos.findBySectorIdAndTipo("MZ-1-012", HardwareService.ELECTROVALVULA)).thenReturn(List.of(valvula));
        when(dispositivos.findByZonaIdAndTipo("MZ-1", HardwareService.MEDIASOMBRA)).thenReturn(List.of(malla));

        service = new HardwareFaultService(dispositivos, sectores, zonas, historial, catalogo, overrides,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static SectorEntity sector(String id) {
        SectorEntity s = new SectorEntity();
        s.setId(id);
        return s;
    }

    private static DispositivoEntity dispositivo(String id, String tipo, String sectorId) {
        DispositivoEntity d = new DispositivoEntity();
        d.setId(id);
        d.setSerial("serial-" + id);
        d.setTipo(tipo);
        d.setSectorId(sectorId);
        return d;
    }

    private static NodeError flow(NodeErrorType type, String sector, String value) {
        return new NodeError("MZ-1", "A4:CF:12:9A:00:01", sector, type, value);
    }

    private static NodeError shade(String value) {
        return new NodeError("MZ-1", "A4:CF:12:9A:00:01", "", NodeErrorType.SHADE_LIMIT_SWITCH, value);
    }

    private String alertaEmitida(NivelAlerta nivel, String regla) {
        var detalle = ArgumentCaptor.forClass(com.yerbanalytics.backend.engine.DetalleAlerta.class);
        verify(historial).registrarAlerta(eq("MZ-1"), eq("Macro-zona 1"), eq(regla), detalle.capture(), eq(AHORA.toEpochMilli()));
        assertThat(detalle.getValue().nivel()).isEqualTo(nivel);
        return detalle.getValue().texto();
    }

    @Nested
    @DisplayName("falla hidráulica (SIN_CAUDAL / CAUDAL_EXCESIVO)")
    class Hidraulica {

        @Test
        @DisplayName("marca la electroválvula del sector con el tipo y desde cuándo, y deja una alerta CRITICAL")
        void marcaLaElectrovalvula() {
            Outcome outcome = service.report(flow(NodeErrorType.NO_FLOW, "MZ-1-012", "0"));

            assertThat(outcome).isEqualTo(Outcome.REGISTERED);
            assertThat(valvula.getFalla()).isEqualTo("SIN_CAUDAL");
            assertThat(valvula.getFallaDesde()).isEqualTo(AHORA.toEpochMilli());
            verify(dispositivos).saveAll(List.of(valvula));
            assertThat(alertaEmitida(NivelAlerta.CRITICAL, "HydraulicFaultRule"))
                    .contains("MZ-1-012").containsIgnoringCase("falla hidráulica").containsIgnoringCase("sin caudal");
        }

        @Test
        @DisplayName("con el sustrato de la zona por debajo del umbral de riego (45 %) el texto pide riego manual")
        void sustratoSecoPideRiegoManual() {
            service.report(flow(NodeErrorType.NO_FLOW, "MZ-1-012", "0"));

            assertThat(alertaEmitida(NivelAlerta.CRITICAL, "HydraulicFaultRule"))
                    .containsIgnoringCase("riego manual").contains("40");
        }

        @Test
        @DisplayName("con el sustrato húmedo la alerta es CRITICAL sin el pedido de riego manual")
        void sustratoHumedoNoPideRiegoManual() {
            zona.setHumSusRaw(60.0);

            service.report(flow(NodeErrorType.EXCESS_FLOW, "MZ-1-012", "180"));

            assertThat(alertaEmitida(NivelAlerta.CRITICAL, "HydraulicFaultRule"))
                    .containsIgnoringCase("caudal excesivo").contains("180 L/h").doesNotContainIgnoringCase("riego manual");
        }

        @Test
        @DisplayName("sin lectura de humedad no se pide riego manual (sin dato no es seco)")
        void sinHumedadNoPideRiegoManual() {
            zona.setHumSusRaw(null);

            service.report(flow(NodeErrorType.NO_FLOW, "MZ-1-012", "0"));

            assertThat(alertaEmitida(NivelAlerta.CRITICAL, "HydraulicFaultRule")).doesNotContainIgnoringCase("riego manual");
        }

        @Test
        @DisplayName("el umbral sale del catálogo: con un umbral de riego menor al sustrato actual no se pide riego manual")
        void elUmbralEsElDelCatalogo() {
            zona.setHumSusRaw(44.0);
            CatalogoParametrosService catalogo = mock(CatalogoParametrosService.class);
            when(catalogo.vigentes()).thenReturn(ParametrosPorPieza.uniforme(ReglaTestSupport.con(Map.of(ParametrosRiego.UMBRAL_HUMEDAD, "40"))));
            HardwareFaultService conOtroUmbral = new HardwareFaultService(dispositivos, sectores, zonas, historial,
                    catalogo, overrides, Clock.fixed(AHORA, ZoneOffset.UTC));

            conOtroUmbral.report(flow(NodeErrorType.NO_FLOW, "MZ-1-012", "0"));

            assertThat(alertaEmitida(NivelAlerta.CRITICAL, "HydraulicFaultRule")).doesNotContainIgnoringCase("riego manual");
        }

        @Test
        @DisplayName("el mismo error mientras la falla sigue puesta no vuelve a alertar ni a guardar")
        void duplicado() {
            service.report(flow(NodeErrorType.NO_FLOW, "MZ-1-012", "0"));

            Outcome segundo = service.report(flow(NodeErrorType.NO_FLOW, "MZ-1-012", "0"));

            assertThat(segundo).isEqualTo(Outcome.DUPLICATE);
            verify(historial, org.mockito.Mockito.times(1)).registrarAlerta(any(), any(), any(), any(), anyLong());
            verify(dispositivos, org.mockito.Mockito.times(1)).saveAll(any());
        }

        @Test
        @DisplayName("un tipo distinto sobre la misma válvula reemplaza la falla y alerta de nuevo")
        void otroTipo() {
            service.report(flow(NodeErrorType.NO_FLOW, "MZ-1-012", "0"));

            Outcome segundo = service.report(flow(NodeErrorType.EXCESS_FLOW, "MZ-1-012", "200"));

            assertThat(segundo).isEqualTo(Outcome.REGISTERED);
            assertThat(valvula.getFalla()).isEqualTo("CAUDAL_EXCESIVO");
            verify(historial, org.mockito.Mockito.times(2)).registrarAlerta(any(), any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("un sector sin electroválvula registrada: no hay qué marcar, pero el operador igual se entera")
        void sinElectrovalvulaRegistrada() {
            when(dispositivos.findBySectorIdAndTipo("MZ-1-012", HardwareService.ELECTROVALVULA)).thenReturn(List.of());

            Outcome outcome = service.report(flow(NodeErrorType.NO_FLOW, "MZ-1-012", "0"));

            assertThat(outcome).isEqualTo(Outcome.REGISTERED);
            verify(dispositivos, never()).saveAll(any());
            assertThat(alertaEmitida(NivelAlerta.CRITICAL, "HydraulicFaultRule")).contains("MZ-1-012");
        }

        @Test
        @DisplayName("un error de caudal sin sector, o de un sector que no es de la zona del tópico, se descarta")
        void sectorInvalido() {
            assertThat(service.report(flow(NodeErrorType.NO_FLOW, "", "0"))).isEqualTo(Outcome.IGNORED);
            assertThat(service.report(flow(NodeErrorType.NO_FLOW, null, "0"))).isEqualTo(Outcome.IGNORED);
            assertThat(service.report(flow(NodeErrorType.NO_FLOW, "MZ-2-001", "0"))).isEqualTo(Outcome.IGNORED);

            assertThat(valvula.getFalla()).isNull();
            verify(dispositivos, never()).saveAll(any());
            verify(historial, never()).registrarAlerta(any(), any(), any(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("falla de la mediasombra (MEDIASOMBRA_FIN_DE_CARRERA)")
    class Mediasombra {

        @Test
        @DisplayName("marca la mediasombra de la macro-zona: la malla es de la zona")
        void marcaLaMallaDeLaZona() {
            Outcome outcome = service.report(shade("CERRADA"));

            assertThat(outcome).isEqualTo(Outcome.REGISTERED);
            assertThat(malla.getFalla()).isEqualTo("MEDIASOMBRA_FIN_DE_CARRERA");
            assertThat(malla.getFallaDesde()).isEqualTo(AHORA.toEpochMilli());
            verify(dispositivos).saveAll(List.of(malla));
        }

        @Test
        @DisplayName("con el final de carrera de 'cerrada' sin activar (la malla quedó abierta) la alerta pide cubrir a mano")
        void quedoAbierta() {
            service.report(shade("CERRADA"));

            assertThat(alertaEmitida(NivelAlerta.CRITICAL, "ShadeFaultRule"))
                    .containsIgnoringCase("falla mecánica").containsIgnoringCase("cubrir a mano");
        }

        @Test
        @DisplayName("con el de 'abierta' sin activar (quedó cerrada) no pide cubrir sino revisar la malla")
        void quedoCerrada() {
            service.report(shade("ABIERTA"));

            assertThat(alertaEmitida(NivelAlerta.CRITICAL, "ShadeFaultRule"))
                    .containsIgnoringCase("falla mecánica").doesNotContainIgnoringCase("cubrir a mano");
        }

        @Test
        @DisplayName("sin saber qué final falló (ack falla_mecanica) la alerta no inventa la posición de la malla")
        void sinValor() {
            service.report(shade(null));

            assertThat(alertaEmitida(NivelAlerta.CRITICAL, "ShadeFaultRule"))
                    .containsIgnoringCase("falla mecánica").doesNotContainIgnoringCase("cubrir a mano");
        }

        @Test
        @DisplayName("el ack falla_mecanica y el error del tópico son el mismo hecho: una sola alerta")
        void mismoHechoPorDosCaminos() {
            Outcome porAck = service.report(shade(null));
            Outcome porTopico = service.report(shade("CERRADA"));

            assertThat(porAck).isEqualTo(Outcome.REGISTERED);
            assertThat(porTopico).isEqualTo(Outcome.DUPLICATE);
            verify(historial, org.mockito.Mockito.times(1)).registrarAlerta(any(), any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("una zona sin mediasombra registrada: no hay qué marcar, pero la alerta sale")
        void sinMediasombraRegistrada() {
            when(dispositivos.findByZonaIdAndTipo("MZ-1", HardwareService.MEDIASOMBRA)).thenReturn(List.of());

            assertThat(service.report(shade("CERRADA"))).isEqualTo(Outcome.REGISTERED);
            verify(dispositivos, never()).saveAll(any());
        }
    }

    @Test
    @DisplayName("una zona que no existe se descarta sin marcar nada")
    void zonaDesconocida() {
        when(zonas.findById("MZ-9")).thenReturn(Optional.empty());

        Outcome outcome = service.report(new NodeError("MZ-9", "x", "MZ-9-001", NodeErrorType.NO_FLOW, "0"));

        assertThat(outcome).isEqualTo(Outcome.IGNORED);
        verify(historial, never()).registrarAlerta(any(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("apagado desde el simulador: no marca ni alerta (el sector no se marca averiado)")
    void apagado() {
        overrides.turnOff(HardwareFaultService.NAME);

        Outcome outcome = service.report(flow(NodeErrorType.NO_FLOW, "MZ-1-012", "0"));

        assertThat(outcome).isEqualTo(Outcome.IGNORED);
        assertThat(valvula.getFalla()).isNull();
        verify(dispositivos, never()).saveAll(any());
        verify(historial, never()).registrarAlerta(any(), any(), any(), any(), anyLong());
    }

    @Nested
    @DisplayName("registro de la reparación")
    class Reparacion {

        @BeforeEach
        void conFalla() {
            valvula.setFalla("SIN_CAUDAL");
            valvula.setFallaDesde(1_000L);
            when(dispositivos.findById("DEV-101")).thenReturn(Optional.of(valvula));
            when(sectores.findById("MZ-1-012")).thenReturn(Optional.of(sectorConZona("MZ-1-012")));
        }

        private SectorEntity sectorConZona(String id) {
            SectorEntity s = sector(id);
            s.setZona(zona);
            return s;
        }

        @Test
        @DisplayName("limpia la falla, deja el evento de Reparación con usuario y detalle, y avisa con una alerta INFO")
        void limpiaYRegistra() {
            service.repair("DEV-101", "  Se destapó el emisor  ", "operario1");

            assertThat(valvula.getFalla()).isNull();
            assertThat(valvula.getFallaDesde()).isNull();
            verify(dispositivos).save(valvula);
            verify(historial).registrarReparacion(eq("operario1"), anyString(), eq("MZ-1-012"), eq("MZ-1"),
                    eq("Macro-zona 1"), eq("SIN_CAUDAL"), eq("Se destapó el emisor"));
            assertThat(alertaEmitida(NivelAlerta.INFO, "HydraulicFaultRule"))
                    .contains("operario1").contains("MZ-1-012");
        }

        @Test
        @DisplayName("reparar la mediasombra de la zona levanta su marca y avisa a la macro-zona")
        void repararLaMalla() {
            malla.setFalla("MEDIASOMBRA_FIN_DE_CARRERA");
            when(dispositivos.findById("DEV-011")).thenReturn(Optional.of(malla));

            service.repair("DEV-011", "Se cambió el fin de carrera", "operario1");

            assertThat(malla.getFalla()).isNull();
            verify(dispositivos).save(malla);
            assertThat(alertaEmitida(NivelAlerta.INFO, "ShadeFaultRule")).contains("Macro-zona MZ-1");
            verify(historial).registrarReparacion(eq("operario1"), anyString(), eq("—"), eq("MZ-1"),
                    eq("Macro-zona 1"), eq("MEDIASOMBRA_FIN_DE_CARRERA"), eq("Se cambió el fin de carrera"));
        }

        @Test
        @DisplayName("el detalle es obligatorio")
        void detalleObligatorio() {
            assertThatThrownBy(() -> service.repair("DEV-101", "   ", "operario1"))
                    .isInstanceOf(InvalidHardwareException.class).hasMessageContaining("detalle");
            assertThatThrownBy(() -> service.repair("DEV-101", null, "operario1"))
                    .isInstanceOf(InvalidHardwareException.class);

            assertThat(valvula.getFalla()).isEqualTo("SIN_CAUDAL");
            verify(dispositivos, never()).save(any());
        }

        @Test
        @DisplayName("un dispositivo que no existe es un dato inválido")
        void inexistente() {
            when(dispositivos.findById("DEV-999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.repair("DEV-999", "x", "operario1"))
                    .isInstanceOf(InvalidHardwareException.class).hasMessageContaining("DEV-999");
        }

        @Test
        @DisplayName("un dispositivo sano no tiene reparación que registrar (409)")
        void sano() {
            valvula.setFalla(null);

            assertThatThrownBy(() -> service.repair("DEV-101", "x", "operario1"))
                    .isInstanceOf(HardwareConflictException.class);
            verify(historial, never()).registrarReparacion(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("metadatos: pieza controlable desde el simulador, consumidora del umbral de riego")
    void metadatos() {
        assertThat(service).isInstanceOf(DemoControllable.class);
        assertThat(service.name()).isEqualTo("HardwareFaultService");
        assertThat(service.label()).isNotBlank().isNotEqualTo(service.name());
        assertThat(service.branch()).isEqualTo(RuleBranch.GLOBAL);
        assertThat(service.priority()).isGreaterThan(50);
        assertThat(service.parametros()).containsExactly(ParametrosRiego.UMBRAL_HUMEDAD);
        assertThat(service.cooldownKeys()).isEmpty();
        assertThat(service.bypassKeys()).isEmpty();
    }
}
