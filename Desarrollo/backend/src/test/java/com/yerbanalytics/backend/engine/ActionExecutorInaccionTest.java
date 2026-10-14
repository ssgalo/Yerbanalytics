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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * El motor sólo decide en el pulso de sensado, así que en cada evaluación se registra el conjunto completo de
 * inacción del sector, haya cambiado o no la decisión: sin deduplicación ni tope temporal.
 */
@DisplayName("ActionExecutor - registro de inacción completo en cada pulso")
class ActionExecutorInaccionTest {

    private static final Instant AHORA = RiegoCtx.instante("10:05");

    private HistorialService historial;
    private ActionExecutor executor;

    @BeforeEach
    void setUp() {
        historial = mock(HistorialService.class);
        ComandoActuadorPublisher publisher = mock(ComandoActuadorPublisher.class);
        when(publisher.publicar(any(), any(), any(), any(), any()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(true, "c", null));
        executor = new ActionExecutor(historial, publisher, new ColaRiego());
    }

    private static RuleContext ctx(String sectorId, Instant ahora) {
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-2");
        SectorEntity sector = RuleContextTestFactory.sectorBasico(sectorId);
        sector.setZona(zona);
        return new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", ahora, null, false);
    }

    /** Lo que emiten tres reglas: dos "sin déficit" y una que bloquea. */
    private static List<RuleAction> decision() {
        return List.of(
                RuleAction.noopInfo("RiegoPorDeficitRule", "Humedad de sustrato 40% sin déficit (umbral 45%)."),
                RuleAction.noopInfo("DeficitCriticoRule", "Humedad de sustrato 40% sin déficit crítico (umbral 35%)."),
                RuleAction.of(ActionType.ABORT_RIEGO, "CicloLecturaRiegoRule", "El sector ya se regó en este ciclo."));
    }

    private void evaluar(String sector, List<RuleAction> acciones, Instant ahora) {
        executor.execute(acciones, ctx(sector, ahora), OrigenEvaluacion.TELEMETRIA);
    }

    private void filas(int cantidad) {
        verify(historial, times(cantidad)).registrarInaccion(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("una evaluación escribe una fila por cada inacción del sector")
    void unaEvaluacion_unaFilaPorInaccion() {
        evaluar("MZ-2-001", decision(), AHORA);

        verify(historial).registrarInaccion(any(), eq(ActionType.NOOP_INFO), eq("RiegoPorDeficitRule"), anyString());
        verify(historial).registrarInaccion(any(), eq(ActionType.NOOP_INFO), eq("DeficitCriticoRule"), anyString());
        verify(historial).registrarInaccion(any(), eq(ActionType.ABORT_RIEGO), eq("CicloLecturaRiegoRule"),
                eq("El sector ya se regó en este ciclo."));
        verifyNoMoreInteractions(historial);
    }

    @Test
    @DisplayName("dos pulsos con la misma decisión escriben dos conjuntos completos")
    void dosPulsosMismaDecision_dosConjuntos() {
        evaluar("MZ-2-001", decision(), AHORA);
        evaluar("MZ-2-001", decision(), AHORA.plusSeconds(4 * 3600));

        filas(6);
    }

    @Test
    @DisplayName("sin tope temporal: dos evaluaciones seguidas del mismo sector escriben las dos")
    void sinTopeTemporal() {
        evaluar("MZ-2-001", decision(), AHORA);
        evaluar("MZ-2-001", decision(), AHORA.plusSeconds(60));

        filas(6);
    }

    @Test
    @DisplayName("las acciones que no son de inacción (ACTIVAR_VALVULA) no escriben filas Info")
    void soloLaInaccionSeRegistra() {
        evaluar("MZ-2-001", List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, "R", "Regar",
                new DetalleRiego(5.0, 600, 40.0, false))), AHORA);

        verify(historial, never()).registrarInaccion(any(), any(), any(), any());
    }

    @Test
    @DisplayName("si falla la escritura se propaga, y la evaluación siguiente vuelve a escribir su conjunto")
    void siFallaSePropaga() {
        doThrow(new IllegalStateException("base caída")).doNothing().when(historial)
                .registrarInaccion(any(), any(), anyString(), anyString());

        assertThatThrownBy(() -> evaluar("MZ-2-001", decision(), AHORA)).isInstanceOf(IllegalStateException.class);
        clearInvocations(historial);
        evaluar("MZ-2-001", decision(), AHORA.plusSeconds(60));

        filas(3);
    }
}
