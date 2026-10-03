package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.riego.ColaRiego;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

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
 * El Registro de Inacción escribe sólo cuando CAMBIA la decisión del sector: con el nodo real publicando cada 30 s,
 * una fila por sector, por regla y por mensaje eran millones por día y por zona sin decir nada nuevo.
 */
@DisplayName("ActionExecutor - registro de inacción sólo cuando cambia la decisión")
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

    private static RuleContext ctx(String sectorId) {
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-2");
        SectorEntity sector = RuleContextTestFactory.sectorBasico(sectorId);
        sector.setZona(zona);
        return new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", AHORA, null, false);
    }

    /** Lo que emiten tres reglas con la humedad dada: dos "no aplica / sin déficit" y una que bloquea. */
    private static List<RuleAction> decision(double humedad) {
        String h = String.valueOf(humedad).replace('.', ',');
        return List.of(
                RuleAction.noopInfo("RiegoPorDeficitRule", "Humedad de sustrato " + h + "% sin déficit (umbral 45%)."),
                RuleAction.noopInfo("DeficitCriticoRule", "Humedad de sustrato " + h + "% sin déficit crítico (umbral 35%)."),
                RuleAction.of(ActionType.ABORT_RIEGO, "CicloLecturaRiegoRule",
                        "El sector tiene un riego en curso hasta las 10:12: no se riega de nuevo (" + h + ")."));
    }

    private void evaluar(String sector, List<RuleAction> acciones, OrigenEvaluacion origen) {
        executor.execute(acciones, ctx(sector), origen);
    }

    private void filas(int cantidad) {
        verify(historial, times(cantidad)).registrarInaccion(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("100 evaluaciones idénticas escriben UNA sola tanda de filas (una por regla)")
    void evaluacionesIdenticas_unaSolaTanda() {
        for (int i = 0; i < 100; i++) {
            evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        }

        filas(3);
    }

    @Test
    @DisplayName("sólo cambia un número del motivo (la humedad exacta): no es otra decisión, no se escribe")
    void cambioDeNumeros_noEscribe() {
        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        evaluar("MZ-2-001", decision(40.5), OrigenEvaluacion.TELEMETRIA);
        evaluar("MZ-2-001", decision(41.0), OrigenEvaluacion.TELEMETRIA);

        filas(3);
    }

    @Test
    @DisplayName("cambia la decisión de UNA regla: se registra el conjunto completo del sector en esa evaluación (el DAG del Historial)")
    void cambiaUnaRegla_seEscribeElConjunto() {
        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        clearInvocations(historial);

        List<RuleAction> otra = List.of(
                RuleAction.noopInfo("RiegoPorDeficitRule", "Humedad de sustrato 40% sin déficit (umbral 45%)."),
                RuleAction.noopInfo("DeficitCriticoRule", "Humedad de sustrato 40% sin déficit crítico (umbral 35%)."),
                RuleAction.of(ActionType.ABORT_RIEGO, "CicloLecturaRiegoRule", "El sector ya se regó en este ciclo de lectura."));
        evaluar("MZ-2-001", otra, OrigenEvaluacion.TELEMETRIA);

        verify(historial).registrarInaccion(any(), eq(ActionType.NOOP_INFO), eq("RiegoPorDeficitRule"), anyString());
        verify(historial).registrarInaccion(any(), eq(ActionType.NOOP_INFO), eq("DeficitCriticoRule"), anyString());
        verify(historial).registrarInaccion(any(), eq(ActionType.ABORT_RIEGO), eq("CicloLecturaRiegoRule"),
                eq("El sector ya se regó en este ciclo de lectura."));
        verifyNoMoreInteractions(historial);
    }

    @Test
    @DisplayName("cambia el tipo de acción de una regla (de NOOP a ABORT) o aparece/desaparece una regla: se registra")
    void cambiaElTipoOElConjunto_seEscribe() {
        evaluar("MZ-2-001", List.of(RuleAction.noopInfo("R", "no aplica")), OrigenEvaluacion.TELEMETRIA);
        evaluar("MZ-2-001", List.of(RuleAction.of(ActionType.ABORT_RIEGO, "R", "no aplica")), OrigenEvaluacion.TELEMETRIA);
        filas(2);

        evaluar("MZ-2-001", List.of(RuleAction.of(ActionType.ABORT_RIEGO, "R", "no aplica"),
                RuleAction.noopInfo("S", "otra")), OrigenEvaluacion.TELEMETRIA);
        filas(4);                                              // el conjunto nuevo (R y S)
    }

    @Test
    @DisplayName("vuelve a una decisión anterior: es un cambio respecto de la última, se registra")
    void volverAUnaDecisionAnterior_seEscribe() {
        evaluar("MZ-2-001", List.of(RuleAction.noopInfo("R", "a")), OrigenEvaluacion.TELEMETRIA);
        evaluar("MZ-2-001", List.of(RuleAction.noopInfo("R", "b")), OrigenEvaluacion.TELEMETRIA);
        evaluar("MZ-2-001", List.of(RuleAction.noopInfo("R", "a")), OrigenEvaluacion.TELEMETRIA);

        filas(3);
    }

    @Test
    @DisplayName("cada sector tiene su propio estado")
    void sectoresIndependientes() {
        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        evaluar("MZ-2-002", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        evaluar("MZ-2-002", decision(40.0), OrigenEvaluacion.TELEMETRIA);

        filas(6);
    }

    @Test
    @DisplayName("las acciones que no son de inacción (ACTIVAR_VALVULA, ALERTA) no escriben filas Info")
    void soloLaInaccionSeDeduplica() {
        evaluar("MZ-2-001", List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, "R", "Regar",
                new DetalleRiego(5.0, 600, 40.0, false))), OrigenEvaluacion.TELEMETRIA);

        verify(historial, never()).registrarInaccion(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ barrido

    @Test
    @DisplayName("el barrido no duplica lo que ya registró la telemetría")
    void barridoNoDuplicaLaTelemetria() {
        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        clearInvocations(historial);

        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.BARRIDO);

        verify(historial, never()).registrarInaccion(any(), any(), any(), any());
    }

    @Test
    @DisplayName("el barrido registra su propia decisión (distinta) una vez y no la repite en cada pasada")
    void barridoRegistraSuDecisionUnaVez() {
        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        clearInvocations(historial);
        List<RuleAction> sinLectura = List.of(RuleAction.of(ActionType.ABORT_RIEGO, "StaleSensorRule", "sin lectura"));

        for (int pasada = 0; pasada < 10; pasada++) {
            evaluar("MZ-2-001", sinLectura, OrigenEvaluacion.BARRIDO);
        }

        filas(1);
    }

    @Test
    @DisplayName("el barrido y la telemetría no se pisan el estado: la telemetría no re-escribe lo suyo tras una pasada del barrido")
    void estadosSeparadosPorOrigen() {
        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        evaluar("MZ-2-001", List.of(RuleAction.of(ActionType.ABORT_RIEGO, "StaleSensorRule", "sin lectura")),
                OrigenEvaluacion.BARRIDO);
        clearInvocations(historial);

        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);

        verify(historial, never()).registrarInaccion(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ reinicio y fallos

    @Test
    @DisplayName("al regenerar la topología se limpia el estado: la misma decisión se vuelve a registrar una vez")
    void reiniciarEstado() {
        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        executor.reiniciarEstado();

        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);

        filas(6);
    }

    @Test
    @DisplayName("si falla la escritura no se da por registrada: la próxima evaluación lo reintenta")
    void siFallaSeReintenta() {
        doThrow(new IllegalStateException("base caída")).doNothing().when(historial)
                .registrarInaccion(any(), any(), anyString(), anyString());

        try {
            evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        } catch (IllegalStateException esperado) {
            // propaga como siempre
        }
        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);
        evaluar("MZ-2-001", decision(40.0), OrigenEvaluacion.TELEMETRIA);

        Mockito.verify(historial, times(1 + 3)).registrarInaccion(any(), any(), anyString(), anyString());
    }
}
