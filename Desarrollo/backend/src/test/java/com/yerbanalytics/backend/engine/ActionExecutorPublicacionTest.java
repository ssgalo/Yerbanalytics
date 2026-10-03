package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caracterización del {@code ActionExecutor} al sacar la publicación MQTT a
 * {@link ComandoActuadorPublisher} (tarea 8.2): el riego actual (válvula con duración del motivo,
 * enganche "Regando"), la bomba y la mediasombra publican exactamente lo mismo que antes.
 */
@DisplayName("ActionExecutor - publicación de comandos")
class ActionExecutorPublicacionTest {

    private HistorialService historial;
    private ComandoActuadorPublisher publisher;
    private ActionExecutor executor;
    private SectorEntity sector;
    private RuleContext ctx;

    @BeforeEach
    void setUp() {
        historial = mock(HistorialService.class);
        publisher = mock(ComandoActuadorPublisher.class);
        when(publisher.publicar(any(), any(), any(), any(), any()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(true, "c-1", null));
        executor = new ActionExecutor(historial, publisher);
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-1");
        sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        sector.setZona(zona);
        ctx = new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", Instant.now(), null, false);
    }

    @Test
    void activarValvulaRegistraElRiegoYPublicaConLaDuracionDelMotivo() {
        executor.execute(List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, "IrrigationRule",
                "Humedad 38%. [tiempo-max-seg=120]")), ctx);

        verify(historial).registrarRiego(sector);
        verify(publisher).publicar("MZ-1", "MZ-1-001", "valve", "ON", Map.of("durationSec", 120));
        assertThat(sector.getActuadorValve()).isEqualTo("Regando");
    }

    @Test
    void conLaValvulaYaRegandoNoVuelveARegarNiARegistrar() {
        sector.setActuadorValve("Regando");

        executor.execute(List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, "IrrigationRule",
                "Humedad 38%. [tiempo-max-seg=120]")), ctx);

        verify(historial, never()).registrarRiego(any());
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
    }

    @Test
    void sinDuracionEnElMotivoUsaLos600SegundosDeSiempre() {
        executor.execute(List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, "IrrigationRule", "sin patrón")), ctx);

        verify(publisher).publicar("MZ-1", "MZ-1-001", "valve", "ON", Map.of("durationSec", 600));
    }

    @Test
    void activarBombaPublicaPumpOn() {
        executor.execute(List.of(RuleAction.of(ActionType.ACTIVAR_BOMBA, "SupplyRule", "dosis")), ctx);

        verify(historial).registrarInsumo(sector);
        verify(publisher).publicar("MZ-1", "MZ-1-001", "pump", "ON", Map.of());
    }

    @Test
    void moverMediasombraPublicaElPorcentajeDelMotivo() {
        executor.execute(List.of(RuleAction.of(ActionType.MOVER_MEDIASOMBRA, "ShadingRule", "cerrar [apertura=40]")), ctx);

        verify(publisher).publicar("MZ-1", "MZ-1-001", "shade", "SET", Map.of("targetPct", 40));
        assertThat(sector.getActuadorShade()).isEqualTo(40);
    }

    @Test
    void unaFallaDePublicacionNoDetieneLaEjecucion() {
        when(publisher.publicar(any(), any(), any(), any(), any()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(false, "c-2", "broker caído"));

        executor.execute(List.of(
                RuleAction.of(ActionType.MOVER_MEDIASOMBRA, "ShadingRule", "cerrar [apertura=40]"),
                RuleAction.noopInfo("X", "m")), ctx);

        verify(historial).registrarInaccion(eq(sector), eq(ActionType.NOOP_INFO), eq("X"), eq("m"));
    }
}
