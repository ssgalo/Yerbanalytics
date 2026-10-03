package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.riego.ColaRiego;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
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
 * La publicación MQTT del {@code ActionExecutor}: la bomba y la mediasombra publican exactamente lo mismo
 * que siempre. La válvula ya NO se publica acá: {@code ACTIVAR_VALVULA} se encola y la abre el despacho
 * (ver {@code ActionExecutorColaTest}).
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
        executor = new ActionExecutor(historial, publisher, new ColaRiego());
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-1");
        sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        sector.setZona(zona);
        ctx = new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", Instant.now(), null, false);
    }

    @Test
    void activarBombaPublicaPumpOn() {
        executor.execute(List.of(RuleAction.of(ActionType.ACTIVAR_BOMBA, "SupplyRule", "dosis")), ctx, OrigenEvaluacion.TELEMETRIA);

        verify(historial).registrarInsumo(sector);
        verify(publisher).publicar("MZ-1", "MZ-1-001", "pump", "ON", Map.of());
    }

    @Test
    void moverMediasombraPublicaElPorcentajeDelMotivo() {
        executor.execute(List.of(RuleAction.of(ActionType.MOVER_MEDIASOMBRA, "ShadingRule", "cerrar [apertura=40]")), ctx, OrigenEvaluacion.TELEMETRIA);

        verify(publisher).publicar("MZ-1", "MZ-1-001", "shade", "SET", Map.of("targetPct", 40));
        assertThat(sector.getActuadorShade()).isEqualTo(40);
    }

    @Test
    void unaFallaDePublicacionNoDetieneLaEjecucion() {
        when(publisher.publicar(any(), any(), any(), any(), any()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(false, "c-2", "broker caído"));

        executor.execute(List.of(
                RuleAction.of(ActionType.MOVER_MEDIASOMBRA, "ShadingRule", "cerrar [apertura=40]"),
                RuleAction.noopInfo("X", "m")), ctx, OrigenEvaluacion.TELEMETRIA);

        verify(historial).registrarInaccion(eq(sector), eq(ActionType.NOOP_INFO), eq("X"), eq("m"));
    }
}
