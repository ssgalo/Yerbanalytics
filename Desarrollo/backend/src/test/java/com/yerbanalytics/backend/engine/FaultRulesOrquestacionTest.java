package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.rules.CicloLecturaRiegoRule;
import com.yerbanalytics.backend.engine.rules.DeficitCriticoRule;
import com.yerbanalytics.backend.engine.rules.FueraDeVentanaRiegoRule;
import com.yerbanalytics.backend.engine.rules.HydraulicFaultRule;
import com.yerbanalytics.backend.engine.rules.IncompleteHardwareRule;
import com.yerbanalytics.backend.engine.rules.ManualLockRule;
import com.yerbanalytics.backend.engine.rules.PausaTrasAplicacionRule;
import com.yerbanalytics.backend.engine.rules.PosponerPorLluviaRule;
import com.yerbanalytics.backend.engine.rules.RiegoPorDeficitRule;
import com.yerbanalytics.backend.engine.rules.StaleSensorRule;
import com.yerbanalytics.backend.engine.rules.SustratoSaturadoRule;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S-05 en el orquestador real (sin Spring): la falla hidráulica saca al sector del riego y del insumo (también de
 * R-02); todo lo demás sigue. La malla es de la macro-zona: su falla la ve la regla de zona ShadeFaultRule.
 */
@DisplayName("S-05 en el orquestador")
class FaultRulesOrquestacionTest {

    private static final HardwareFaults VALVULA = new HardwareFaults("SIN_CAUDAL");

    private final RuleOverridesStore overrides = new RuleOverridesStore();
    private RuleOrchestrator orquestador;

    /** Regla de una rama que siempre pide actuar: si corre, el test lo ve en sus acciones. */
    private static Rule ejecutora(String nombre, int prioridad, RuleBranch rama, ActionType tipo) {
        return new Rule() {
            @Override public int priority() { return prioridad; }
            @Override public String name() { return nombre; }
            @Override public RuleBranch branch() { return rama; }
            @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
                return List.of(RuleAction.of(tipo, nombre, "actúa"));
            }
        };
    }

    @BeforeEach
    void setUp() {
        CatalogoParametrosService catalogo = mock(CatalogoParametrosService.class);
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        List<Rule> reglas = new ArrayList<>(List.of(
                new ManualLockRule(mock(ManualLockRepository.class)), new IncompleteHardwareRule(),
                new HydraulicFaultRule(), new StaleSensorRule(), new SustratoSaturadoRule(),
                new CicloLecturaRiegoRule(), new DeficitCriticoRule(), new FueraDeVentanaRiegoRule(),
                new PausaTrasAplicacionRule(), new PosponerPorLluviaRule(), new RiegoPorDeficitRule(),
                ejecutora("Dosis", 12, RuleBranch.INSUMO, ActionType.ACTIVAR_BOMBA)));
        Collections.shuffle(reglas);
        orquestador = new RuleOrchestrator(reglas, catalogo, new TrazaEvaluacionStore(), overrides);
    }

    private ResultadoEvaluacion evaluar(double humedad, HardwareFaults faults) {
        return orquestador.evaluate(RiegoCtx.a("10:05").humedad(humedad).inicioCiclo("10:00").faults(faults).build(),
                OrigenEvaluacion.TELEMETRIA);
    }

    private static List<RuleAction> de(ResultadoEvaluacion r, ActionType tipo) {
        return r.acciones().stream().filter(a -> a.type() == tipo).toList();
    }

    private static TrazaRegla regla(ResultadoEvaluacion r, String nombre) {
        return r.traza().reglas().stream().filter(t -> t.ruleId().equals(nombre)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("falla hidráulica con sustrato seco: R-01 no riega, la dosis se omite y se retira el riego encolado")
    void fallaHidraulica() {
        ResultadoEvaluacion r = evaluar(40.0, VALVULA);

        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
        assertThat(de(r, ActionType.ABORT_RIEGO)).extracting(RuleAction::ruleName).contains("HydraulicFaultRule");
        assertThat(de(r, ActionType.ABORT_RIEGO).get(0).detalle()).isEqualTo(CancelaRiego.TODAS);
        assertThat(regla(r, "RiegoPorDeficitRule").estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
        assertThat(regla(r, "RiegoPorDeficitRule").bloqueadaPor()).isEqualTo("HydraulicFaultRule");
        assertThat(de(r, ActionType.ACTIVAR_BOMBA)).isEmpty();
        assertThat(regla(r, "Dosis").estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
    }

    @Test
    @DisplayName("R-02 tampoco exceptúa a la falla hidráulica: con déficit crítico el sector averiado no se riega")
    void r02NoSeExceptua() {
        ResultadoEvaluacion r = evaluar(20.0, VALVULA);

        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
        assertThat(regla(r, "DeficitCriticoRule").estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
    }

    @Test
    @DisplayName("sin falla, el mismo sector con déficit crítico sí se riega (control del test anterior)")
    void controlSinFalla() {
        ResultadoEvaluacion r = evaluar(20.0, HardwareFaults.NONE);

        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isNotEmpty();
        assertThat(regla(r, "HydraulicFaultRule").acciones()).extracting(a -> a.tipo()).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("el orden de la traza: bloqueo manual, hardware incompleto, falla hidráulica, frescura del sensor")
    void ordenDeLaTraza() {
        List<String> orden = evaluar(40.0, HardwareFaults.NONE).traza().reglas().stream().map(TrazaRegla::ruleId).toList();

        assertThat(orden.subList(0, 4)).containsExactly(
                "ManualLockRule", "IncompleteHardwareRule", "HydraulicFaultRule", "StaleSensorRule");
    }

    @Test
    @DisplayName("un bloqueo manual gana: con ABORT_ALL las reglas de falla ni se alcanzan")
    void elBloqueoManualGana() {
        ResultadoEvaluacion r = orquestador.evaluate(
                RiegoCtx.a("10:05").humedad(40.0).bloqueado().faults(VALVULA).build(), OrigenEvaluacion.TELEMETRIA);

        assertThat(regla(r, "HydraulicFaultRule").estado()).isEqualTo(EstadoRegla.NO_ALCANZADA);
    }

    @Test
    @DisplayName("apagada desde el simulador: no actúa y el sector averiado se evalúa como si estuviera sano")
    void apagada() {
        overrides.put("HydraulicFaultRule", new RuleOverride(false, Map.of(), Set.of()));

        ResultadoEvaluacion r = evaluar(40.0, VALVULA);

        assertThat(regla(r, "HydraulicFaultRule").estado()).isEqualTo(EstadoRegla.OMITIDA_DESHABILITADA);
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).hasSize(1);
    }

    @Test
    @DisplayName("bypass 'fault': el sector averiado vuelve a evaluarse")
    void bypass() {
        overrides.put("HydraulicFaultRule", new RuleOverride(true, Map.of(), Set.of("fault")));

        ResultadoEvaluacion r = evaluar(40.0, new HardwareFaults("SIN_CAUDAL"));

        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).hasSize(1);
        assertThat(de(r, ActionType.ACTIVAR_BOMBA)).hasSize(1);
    }
}
