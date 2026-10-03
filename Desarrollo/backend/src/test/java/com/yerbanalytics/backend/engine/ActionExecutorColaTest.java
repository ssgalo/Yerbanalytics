package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.riego.ColaRiego;
import com.yerbanalytics.backend.engine.riego.SolicitudRiego;
import com.yerbanalytics.backend.engine.rules.DeficitCriticoRule;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tarea 10.1: el {@code ActionExecutor} ENCOLA el riego (no publica) y persiste las alertas una vez por
 * (macro-zona, regla, ciclo de lectura).
 */
@DisplayName("ActionExecutor - cola de riego y alertas")
class ActionExecutorColaTest {

    private static final Instant AHORA = RiegoCtx.instante("10:05");

    private HistorialService historial;
    private ComandoActuadorPublisher publisher;
    private ColaRiego cola;
    private ActionExecutor executor;

    @BeforeEach
    void setUp() {
        historial = mock(HistorialService.class);
        publisher = mock(ComandoActuadorPublisher.class);
        when(publisher.publicar(any(), any(), any(), any(), any()))
                .thenReturn(new ComandoActuadorPublisher.Resultado(true, "c", null));
        cola = new ColaRiego();
        executor = new ActionExecutor(historial, publisher, cola);
    }

    private static RuleContext ctx(String zonaId, int n, String inicioCiclo) {
        ZonaEntity zona = RuleContextTestFactory.zonaBasica(zonaId);
        zona.setName("Macro-zona " + zonaId);
        SectorEntity sector = RuleContextTestFactory.sectorBasico(String.format("%s-%03d", zonaId, n));
        sector.setN(n);
        sector.setZona(zona);
        ContextoRiego riego = new ContextoRiego(RiegoCtx.instante(inicioCiclo), null, null, null, null, null);
        return new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", AHORA, null, false, riego);
    }

    private static RuleAction riego(double litros, int seg) {
        return RuleAction.of(ActionType.ACTIVAR_VALVULA, "RiegoPorDeficitRule", "Regar",
                new DetalleRiego(litros, seg, 40.0, false));
    }

    private static RuleAction alerta(String regla, NivelAlerta nivel, String texto) {
        return RuleAction.of(ActionType.ALERTA, regla, "motivo", new DetalleAlerta(nivel, texto));
    }

    @Test
    @DisplayName("ACTIVAR_VALVULA en telemetría encola la solicitud tipada y NO publica ni registra")
    void activarValvula_encola() {
        executor.execute(List.of(riego(5.0, 600)), ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);

        assertThat(cola.pendientes("MZ-2")).hasSize(1);
        SolicitudRiego s = cola.pendientes("MZ-2").get(0);
        assertThat(s.sectorId()).isEqualTo("MZ-2-007");
        assertThat(s.numero()).isEqualTo(7);
        assertThat(s.regla()).isEqualTo("RiegoPorDeficitRule");
        assertThat(s.detalle()).isEqualTo(new DetalleRiego(5.0, 600, 40.0, false));
        assertThat(s.solicitadaEn()).isEqualTo(AHORA);
        verify(publisher, never()).publicar(any(), any(), any(), any(), any());
        verify(historial, never()).registrarRiego(any(), any(), any(), anyLong());
        verify(historial, never()).registrarRiego(any());
    }

    @Test
    @DisplayName("ya no engancha el estado 'Regando' en la entidad del sector")
    void noEngancha() {
        RuleContext ctx = ctx("MZ-2", 7, "10:00");

        executor.execute(List.of(riego(5.0, 600)), ctx, OrigenEvaluacion.TELEMETRIA);

        assertThat(ctx.sector().getActuadorValve()).isEqualTo("Cerrada");
    }

    @Test
    @DisplayName("una nueva decisión con ACTIVAR_VALVULA reemplaza la solicitud (otro volumen)")
    void reemplaza() {
        executor.execute(List.of(riego(5.0, 600)), ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);
        executor.execute(List.of(riego(6.0, 720)), ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);

        assertThat(cola.pendientes("MZ-2")).hasSize(1);
        assertThat(cola.pendientes("MZ-2").get(0).detalle().duracionSeg()).isEqualTo(720);
    }

    private static RuleAction cancela(String regla, CancelaRiego alcance) {
        return RuleAction.of(ActionType.ABORT_RIEGO, regla, "motivo", alcance);
    }

    private static RuleAction riegoCritico(double litros, int seg) {
        return RuleAction.of(ActionType.ACTIVAR_VALVULA, DeficitCriticoRule.NAME, "Crítico",
                new DetalleRiego(litros, seg, 30.0, false));
    }

    private void encolar(int n) {
        executor.execute(List.of(riego(5.0, 600)), ctx("MZ-2", n, "10:00"), OrigenEvaluacion.TELEMETRIA);
    }

    @Test
    @DisplayName("una cancelación explícita de seguridad (R-04) retira la solicitud del sector, y sólo la de ese sector")
    void cancelacionExplicita_retira() {
        encolar(7);
        encolar(8);

        executor.execute(List.of(cancela("SustratoSaturadoRule", CancelaRiego.TODAS)),
                ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);

        assertThat(cola.pendientes("MZ-2")).extracting(SolicitudRiego::sectorId).containsExactly("MZ-2-008");
        // y el motivo sigue quedando en el historial como inacción
        verify(historial).registrarInaccion(any(), eq(ActionType.ABORT_RIEGO), eq("SustratoSaturadoRule"), eq("motivo"));
    }

    @Test
    @DisplayName("la humedad se recuperó (R-01 y R-02 ya no piden riego): la solicitud de la ronda NO se retira")
    void sinActivar_noRetira() {
        encolar(7);

        executor.execute(List.of(RuleAction.noopInfo("RiegoPorDeficitRule", "no aplica"),
                RuleAction.noopInfo("DeficitCriticoRule", "sin déficit crítico")),
                ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);

        assertThat(cola.contiene("MZ-2-007")).isTrue();
    }

    @Test
    @DisplayName("una evaluación sin ninguna acción tampoco retira")
    void sinAcciones_noRetira() {
        encolar(7);

        executor.execute(List.of(), ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);

        assertThat(cola.contiene("MZ-2-007")).isTrue();
    }

    @Test
    @DisplayName("un ABORT_RIEGO sin marca de cancelación (regla de ciclo, tope de R-02, R-03) no retira")
    void abortSinMarca_noRetira() {
        encolar(7);

        executor.execute(List.of(RuleAction.of(ActionType.ABORT_RIEGO, "CicloLecturaRiegoRule", "riego en curso")),
                ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);
        executor.execute(List.of(RuleAction.of(ActionType.POSTPONE_RIEGO, "PosponerPorLluviaRule", "lluvia")),
                ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);

        assertThat(cola.contiene("MZ-2-007")).isTrue();
    }

    @Test
    @DisplayName("ventana cerrada (R-05) y pausa (R-06) retiran las solicitudes de R-01 pero no las de R-02")
    void ventanaYPausa_soloRetiranR01() {
        encolar(7);                                                       // R-01
        executor.execute(List.of(riegoCritico(6.0, 720)), ctx("MZ-2", 8, "10:00"), OrigenEvaluacion.TELEMETRIA);   // R-02

        for (String regla : List.of("FueraDeVentanaRiegoRule", "PausaTrasAplicacionRule")) {
            executor.execute(List.of(cancela(regla, CancelaRiego.SOLO_DEFICIT_COMUN)),
                    ctx("MZ-2", 8, "10:00"), OrigenEvaluacion.TELEMETRIA);
        }
        assertThat(cola.contiene("MZ-2-008")).as("la de R-02 no depende de la ventana ni de la pausa").isTrue();

        executor.execute(List.of(cancela("FueraDeVentanaRiegoRule", CancelaRiego.SOLO_DEFICIT_COMUN)),
                ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);
        assertThat(cola.contiene("MZ-2-007")).isFalse();
    }

    @Test
    @DisplayName("bloqueo manual o saturación (alcance total) retiran también la solicitud de R-02")
    void seguridadTotal_retiraTambienR02() {
        executor.execute(List.of(riegoCritico(6.0, 720)), ctx("MZ-2", 8, "10:00"), OrigenEvaluacion.TELEMETRIA);

        executor.execute(List.of(cancela("ManualLockRule", CancelaRiego.TODAS)),
                ctx("MZ-2", 8, "10:00"), OrigenEvaluacion.TELEMETRIA);

        assertThat(cola.contiene("MZ-2-008")).isFalse();
    }

    @Test
    @DisplayName("una decisión más grave actualiza la solicitud encolada (R-01 → R-02) sin duplicarla")
    void r01ASiR02_actualiza() {
        encolar(7);

        executor.execute(List.of(riegoCritico(6.0, 720)), ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);

        assertThat(cola.pendientes("MZ-2")).hasSize(1);
        assertThat(cola.pendientes("MZ-2").get(0).regla()).isEqualTo(DeficitCriticoRule.NAME);
        assertThat(cola.pendientes("MZ-2").get(0).detalle().duracionSeg()).isEqualTo(720);
    }

    @Test
    @DisplayName("una decisión menos grave NO degrada la solicitud encolada (R-02 → R-01 si la humedad mejoró un poco)")
    void r02AR01_noDegrada() {
        executor.execute(List.of(riegoCritico(6.0, 720)), ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);

        encolar(7);

        assertThat(cola.pendientes("MZ-2")).hasSize(1);
        assertThat(cola.pendientes("MZ-2").get(0).regla()).isEqualTo(DeficitCriticoRule.NAME);
        assertThat(cola.pendientes("MZ-2").get(0).detalle().duracionSeg()).isEqualTo(720);
    }

    @Test
    @DisplayName("el barrido NO toca la cola: ni retira ni encola")
    void barrido_noToca() {
        executor.execute(List.of(riego(5.0, 600)), ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);

        executor.execute(List.of(RuleAction.of(ActionType.ABORT_RIEGO, "StaleSensorRule", "sin lectura")),
                ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.BARRIDO);
        executor.execute(List.of(riego(6.0, 720)), ctx("MZ-2", 9, "10:00"), OrigenEvaluacion.BARRIDO);

        assertThat(cola.pendientes("MZ-2")).extracting(SolicitudRiego::sectorId).containsExactly("MZ-2-007");
        assertThat(cola.pendientes("MZ-2").get(0).detalle().duracionSeg()).isEqualTo(600);
    }

    @Test
    @DisplayName("una orden de 0 s o sin detalle no se encola (y cancela la anterior del sector)")
    void ordenInvalida_noSeEncola() {
        executor.execute(List.of(riego(5.0, 600)), ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);
        executor.execute(List.of(riego(0.0, 0)), ctx("MZ-2", 7, "10:00"), OrigenEvaluacion.TELEMETRIA);
        assertThat(cola.contiene("MZ-2-007")).isFalse();

        executor.execute(List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, "X", "sin detalle")),
                ctx("MZ-2", 8, "10:00"), OrigenEvaluacion.TELEMETRIA);
        assertThat(cola.contiene("MZ-2-008")).isFalse();
    }

    // ------------------------------------------------------------------ alertas

    @Test
    @DisplayName("la alerta de 100 sectores de la misma MZ, regla y ciclo se persiste UNA vez")
    void alerta_unaVezPorMzReglaYCiclo() {
        for (int n = 1; n <= 100; n++) {
            executor.execute(List.of(alerta("DeficitCriticoRule", NivelAlerta.CRITICAL, "Déficit hídrico crítico")),
                    ctx("MZ-2", n, "10:00"), OrigenEvaluacion.TELEMETRIA);
        }

        verify(historial, times(1)).registrarAlerta(eq("MZ-2"), eq("Macro-zona MZ-2"), eq("DeficitCriticoRule"),
                eq(new DetalleAlerta(NivelAlerta.CRITICAL, "Déficit hídrico crítico")), eq(AHORA.toEpochMilli()));
    }

    @Test
    @DisplayName("otro ciclo, otra MZ u otra regla vuelven a alertar")
    void alerta_seRepiteEnOtroCicloZonaORegla() {
        RuleAction critica = alerta("DeficitCriticoRule", NivelAlerta.CRITICAL, "Déficit hídrico crítico");
        executor.execute(List.of(critica), ctx("MZ-2", 1, "10:00"), OrigenEvaluacion.TELEMETRIA);
        executor.execute(List.of(critica), ctx("MZ-2", 2, "10:00"), OrigenEvaluacion.TELEMETRIA);   // mismo ciclo: no
        executor.execute(List.of(critica), ctx("MZ-2", 1, "14:00"), OrigenEvaluacion.TELEMETRIA);   // ciclo siguiente: sí
        executor.execute(List.of(critica), ctx("MZ-3", 1, "14:00"), OrigenEvaluacion.TELEMETRIA);   // otra MZ: sí
        executor.execute(List.of(alerta("SustratoSaturadoRule", NivelAlerta.WARNING, "Sustrato saturado")),
                ctx("MZ-3", 1, "14:00"), OrigenEvaluacion.TELEMETRIA);                              // otra regla: sí

        verify(historial, times(4)).registrarAlerta(any(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("una alerta que no se pudo persistir se reintenta en la próxima evaluación del ciclo")
    void alerta_siFallaLaPersistenciaSeReintenta() {
        RuleAction critica = alerta("DeficitCriticoRule", NivelAlerta.CRITICAL, "Déficit hídrico crítico");
        org.mockito.Mockito.doThrow(new IllegalStateException("base caída")).doNothing().when(historial)
                .registrarAlerta(any(), any(), any(), any(), anyLong());

        executor.execute(List.of(critica), ctx("MZ-2", 1, "10:00"), OrigenEvaluacion.TELEMETRIA);
        executor.execute(List.of(critica), ctx("MZ-2", 2, "10:00"), OrigenEvaluacion.TELEMETRIA);
        executor.execute(List.of(critica), ctx("MZ-2", 3, "10:00"), OrigenEvaluacion.TELEMETRIA);

        verify(historial, times(2)).registrarAlerta(any(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("sin inicio de ciclo en el contexto (barrido) el ciclo se calcula del reloj y del intervalo")
    void alerta_sinCicloEnElContexto() {
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-2");
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-2-001");
        sector.setZona(zona);
        RuleContext vacio = new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", AHORA, null, false);
        RuleAction a = alerta("SustratoSaturadoRule", NivelAlerta.WARNING, "x");

        executor.execute(List.of(a), vacio, OrigenEvaluacion.BARRIDO);
        executor.execute(List.of(a), vacio, OrigenEvaluacion.BARRIDO);

        verify(historial, times(1)).registrarAlerta(any(), any(), any(), any(), anyLong());
    }
}
