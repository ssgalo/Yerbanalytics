package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.rules.CicloLecturaRiegoRule;
import com.yerbanalytics.backend.engine.rules.DeficitCriticoRule;
import com.yerbanalytics.backend.engine.rules.FueraDeVentanaRiegoRule;
import com.yerbanalytics.backend.engine.rules.ManualLockRule;
import com.yerbanalytics.backend.engine.rules.PausaTrasAplicacionRule;
import com.yerbanalytics.backend.engine.rules.PosponerPorLluviaRule;
import com.yerbanalytics.backend.engine.rules.RiegoPorDeficitRule;
import com.yerbanalytics.backend.engine.rules.StaleSensorRule;
import com.yerbanalytics.backend.engine.rules.SustratoSaturadoRule;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Las reglas de riego juntas, en el orquestador real (sin Spring): quién gana, qué corta a quién y qué
 * muestra la traza. Cubre las combinaciones que cada regla aislada no puede probar.
 */
@DisplayName("Reglas de riego en el orquestador")
class RiegoOrquestacionTest {

    private RuleOrchestrator orquestador;

    @BeforeEach
    void setUp() {
        CatalogoParametrosService catalogo = mock(CatalogoParametrosService.class);
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        List<Rule> reglas = new java.util.ArrayList<>(List.of(
                new ManualLockRule(mock(ManualLockRepository.class)), new StaleSensorRule(),
                new CicloLecturaRiegoRule(), new SustratoSaturadoRule(), new DeficitCriticoRule(),
                new FueraDeVentanaRiegoRule(), new PausaTrasAplicacionRule(), new PosponerPorLluviaRule(),
                new RiegoPorDeficitRule()));
        Collections.shuffle(reglas);   // el orquestador ordena por prioridad: el orden de entrada no importa
        orquestador = new RuleOrchestrator(reglas, catalogo, new TrazaEvaluacionStore());
    }

    private ResultadoEvaluacion evaluar(RiegoCtx ctx) {
        return orquestador.evaluate(ctx.build(), OrigenEvaluacion.TELEMETRIA);
    }

    private static WeatherForecast lluviaFuerte(RiegoCtx base) {
        return RiegoCtx.pronostico(base.ahora(), new double[]{90, 90, 90, 90}, new double[]{3, 3, 3, 3});
    }

    private static List<RuleAction> de(ResultadoEvaluacion r, ActionType tipo) {
        return r.acciones().stream().filter(a -> a.type() == tipo).toList();
    }

    private static TrazaRegla regla(ResultadoEvaluacion r, String nombre) {
        return r.traza().reglas().stream().filter(t -> t.ruleId().equals(nombre)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("R-02 gana: h 34 + lluvia fuerte + 23:30 (fuera de ventana) + aplicación hace 1 h → una sola válvula")
    void r02GanaATodo() {
        RiegoCtx base = RiegoCtx.a("23:30");
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("23:30").humedad(34.0).forecast(lluviaFuerte(base))
                .ultimaAplicacionHace(1, 0, 0).inicioCiclo("22:00"));

        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).hasSize(1);
        assertThat(de(r, ActionType.ACTIVAR_VALVULA).get(0).ruleName()).isEqualTo("DeficitCriticoRule");
        assertThat(de(r, ActionType.ALERTA)).hasSize(1);
        assertThat(r.acciones()).noneMatch(a -> a.type() == ActionType.POSTPONE_RIEGO || a.type() == ActionType.ABORT_RIEGO);
        for (String compuerta : List.of("FueraDeVentanaRiegoRule", "PausaTrasAplicacionRule", "PosponerPorLluviaRule")) {
            TrazaRegla t = regla(r, compuerta);
            assertThat(t.estado()).isEqualTo(EstadoRegla.EVALUADA);
            assertThat(t.acciones()).extracting(a -> a.tipo()).containsExactly(ActionType.NOOP_INFO);
            assertThat(t.acciones().get(0).motivo()).containsIgnoringCase("no aplica");
        }
        assertThat(regla(r, "RiegoPorDeficitRule").acciones().get(0).motivo()).contains("R-02");
    }

    @Test
    @DisplayName("h 40 con lluvia fuerte de día → POSTPONE_RIEGO y R-01 omitida por la rama bloqueada")
    void lluviaPospone() {
        RiegoCtx base = RiegoCtx.a("10:05");
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("10:05").humedad(40.0).forecast(lluviaFuerte(base)).inicioCiclo("10:00"));

        assertThat(de(r, ActionType.POSTPONE_RIEGO)).hasSize(1);
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
        assertThat(de(r, ActionType.ALERTA)).extracting(a -> ((DetalleAlerta) a.detalle()).nivel())
                .containsExactly(NivelAlerta.INFO);
        TrazaRegla r01 = regla(r, "RiegoPorDeficitRule");
        assertThat(r01.estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
        assertThat(r01.bloqueadaPor()).isEqualTo("PosponerPorLluviaRule");
    }

    @Test
    @DisplayName("h 40 a las 19:00 → R-05 corta y R-06, R-03 y R-01 quedan omitidas")
    void fueraDeVentana() {
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("19:00").humedad(40.0).inicioCiclo("18:00"));

        assertThat(de(r, ActionType.ABORT_RIEGO)).extracting(RuleAction::ruleName).containsExactly("FueraDeVentanaRiegoRule");
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
        assertThat(regla(r, "PosponerPorLluviaRule").estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
        assertThat(regla(r, "RiegoPorDeficitRule").estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
    }

    @Test
    @DisplayName("h 40 con una aplicación hace 2 h → R-06 corta sólo a este sector")
    void pausaTrasAplicacion() {
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("11:00").humedad(40.0).inicioCiclo("10:00").ultimaAplicacionHace(2, 0, 0));

        assertThat(de(r, ActionType.ABORT_RIEGO)).extracting(RuleAction::ruleName).containsExactly("PausaTrasAplicacionRule");
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
    }

    @Test
    @DisplayName("bloqueo manual → ABORT_ALL y nada en la rama RIEGO (tampoco R-02)")
    void bloqueoManual() {
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("10:05").humedad(20.0).inicioCiclo("10:00").bloqueado());

        assertThat(de(r, ActionType.ABORT_ALL)).hasSize(1);
        assertThat(r.acciones()).noneMatch(a -> a.type() == ActionType.ACTIVAR_VALVULA || a.type() == ActionType.ALERTA);
        assertThat(regla(r, "DeficitCriticoRule").estado()).isEqualTo(EstadoRegla.NO_ALCANZADA);
        assertThat(regla(r, "RiegoPorDeficitRule").estado()).isEqualTo(EstadoRegla.NO_ALCANZADA);
    }

    @Test
    @DisplayName("humedad de sustrato congelada (la sonda cayó) → StaleSensorRule corta, ni R-02 riega")
    void humedadCongelada() {
        RuleContext ctx = RiegoCtx.a("10:05").humedad(20.0).inicioCiclo("10:00").build();
        ctx.zona().setHumSusTs(ctx.now().toEpochMilli() - 600_000);

        ResultadoEvaluacion r = orquestador.evaluate(ctx, OrigenEvaluacion.TELEMETRIA);

        assertThat(de(r, ActionType.ABORT_RIEGO)).extracting(RuleAction::ruleName).containsExactly("StaleSensorRule");
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
    }

    @Test
    @DisplayName("h 80 → R-04 bloquea con alerta WARNING y nada riega")
    void saturado() {
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("10:05").humedad(80.0).inicioCiclo("10:00"));

        assertThat(de(r, ActionType.ABORT_RIEGO)).extracting(RuleAction::ruleName).containsExactly("SustratoSaturadoRule");
        assertThat(de(r, ActionType.ALERTA)).extracting(a -> ((DetalleAlerta) a.detalle()).nivel())
                .containsExactly(NivelAlerta.WARNING);
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
    }

    @Test
    @DisplayName("ya regó en el ciclo con déficit común (h 40): la regla de ciclo corta a R-01")
    void unRiegoPorCicloParaR01() {
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("13:59").humedad(40.0).inicioCiclo("10:00").ultimoRiego("10:12"));

        assertThat(de(r, ActionType.ABORT_RIEGO)).extracting(RuleAction::ruleName).containsExactly("CicloLecturaRiegoRule");
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
        assertThat(de(r, ActionType.ALERTA)).isEmpty();
    }

    @Test
    @DisplayName("R-01 regó a las 10:12 y a las 13:30 la humedad es 30: R-02 riega (la regla de ciclo no la frena)")
    void r02RiegaAunqueR01YaRegoEnElCiclo() {
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("13:30").humedad(30.0).inicioCiclo("10:00").ultimoRiego("10:12"));

        assertThat(de(r, ActionType.ABORT_RIEGO)).isEmpty();
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).extracting(RuleAction::ruleName).containsExactly("DeficitCriticoRule");
        assertThat(de(r, ActionType.ALERTA)).hasSize(1);
    }

    @Test
    @DisplayName("con un riego en curso nadie riega: ni R-01 (h 40) ni R-02 (h 20)")
    void riegoEnCursoBloqueaATodos() {
        for (double h : new double[]{40.0, 20.0}) {
            ResultadoEvaluacion r = evaluar(RiegoCtx.a("10:05").humedad(h).inicioCiclo("10:00").riegoEnCursoHasta("10:10"));

            assertThat(de(r, ActionType.ABORT_RIEGO)).extracting(RuleAction::ruleName).containsExactly("CicloLecturaRiegoRule");
            assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
            assertThat(de(r, ActionType.ALERTA)).isEmpty();
        }
    }

    @Test
    @DisplayName("el tope de 12 h de R-02 se sigue respetando aunque el ciclo ya no la frene")
    void topeDeR02ConRiegoPrevioEnElCiclo() {
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("13:30").humedad(30.0).inicioCiclo("10:00").ultimoRiego("10:12")
                .ultimoRiegoCriticoHace(3, 18));

        assertThat(de(r, ActionType.ABORT_RIEGO)).extracting(RuleAction::ruleName).containsExactly("DeficitCriticoRule");
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
    }

    @Test
    @DisplayName("riego crítico hace 11 h 59 min y h 30 → el tope corta, sin válvula ni alerta")
    void topeDeR02() {
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("14:00").humedad(30.0).inicioCiclo("14:00").ultimoRiegoCriticoHace(11, 59));

        assertThat(de(r, ActionType.ABORT_RIEGO)).extracting(RuleAction::ruleName).containsExactly("DeficitCriticoRule");
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
        assertThat(de(r, ActionType.ALERTA)).isEmpty();
    }

    @Test
    @DisplayName("h 40 de día sin lluvia ni pausa → una sola válvula, de R-01 (5 L, 600 s)")
    void camino_feliz() {
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("10:05").humedad(40.0).inicioCiclo("10:00"));

        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).hasSize(1);
        RuleAction a = de(r, ActionType.ACTIVAR_VALVULA).get(0);
        assertThat(a.ruleName()).isEqualTo("RiegoPorDeficitRule");
        DetalleRiego d = (DetalleRiego) a.detalle();
        assertThat(d.volumenL()).isEqualTo(5.0);
        assertThat(d.duracionSeg()).isEqualTo(600);
        assertThat(r.acciones()).noneMatch(x -> x.type() == ActionType.ABORT_RIEGO || x.type() == ActionType.POSTPONE_RIEGO);
    }

    @Test
    @DisplayName("sin pronóstico (O-01) se asume que no llueve: R-01 riega")
    void sinPronostico_riega() {
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("10:05").humedad(40.0).inicioCiclo("10:00"));

        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).hasSize(1);
    }

    @Test
    @DisplayName("sin lectura de humedad no se riega")
    void sinHumedad_noRiega() {
        ResultadoEvaluacion r = evaluar(RiegoCtx.a("10:05").inicioCiclo("10:00"));

        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
    }

    @Test
    @DisplayName("el orden de la rama RIEGO es Ciclo → R-04 → R-02 → R-05 → R-06 → R-03 → R-01")
    void ordenDeLaRama() {
        assertThat(orquestador.getRules().stream().filter(x -> x.branch() == RuleBranch.RIEGO).map(Rule::name).toList())
                .containsExactly("CicloLecturaRiegoRule", "SustratoSaturadoRule", "DeficitCriticoRule",
                        "FueraDeVentanaRiegoRule", "PausaTrasAplicacionRule", "PosponerPorLluviaRule", "RiegoPorDeficitRule");
    }
}
