package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosPorPieza;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.TestRuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.rules.CicloLecturaRiegoRule;
import com.yerbanalytics.backend.engine.rules.DeficitCriticoRule;
import com.yerbanalytics.backend.engine.rules.FueraDeVentanaRiegoRule;
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
 * S-04 en el orquestador real (sin Spring): la rama a la que le falta un actuador se corta y las demás siguen.
 * Una regla de insumo y una de mediasombra de mentira acompañan a las de riego reales, para ver qué rama corta qué.
 */
@DisplayName("S-04 en el orquestador")
class IncompleteHardwareOrquestacionTest {

    private static final HardwareCoverage SIN_ELECTROVALVULA = new HardwareCoverage(false, true, true);
    private static final HardwareCoverage SIN_BOMBA = new HardwareCoverage(true, false, true);

    private final TestRuleOverrides overrides = new TestRuleOverrides();
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
        when(catalogo.vigentes()).thenReturn(ParametrosPorPieza.uniforme(ReglaTestSupport.fabrica()));
        List<Rule> reglas = new ArrayList<>(List.of(
                new ManualLockRule(mock(ManualLockRepository.class)), new IncompleteHardwareRule(), new StaleSensorRule(),
                new CicloLecturaRiegoRule(), new SustratoSaturadoRule(), new DeficitCriticoRule(),
                new FueraDeVentanaRiegoRule(), new PausaTrasAplicacionRule(), new PosponerPorLluviaRule(),
                new RiegoPorDeficitRule(),
                ejecutora("Dosis", 12, RuleBranch.INSUMO, ActionType.ACTIVAR_BOMBA),
                ejecutora("Sombra", 13, RuleBranch.MEDIASOMBRA, ActionType.MOVER_MEDIASOMBRA)));
        Collections.shuffle(reglas);
        orquestador = new RuleOrchestrator(reglas, catalogo, new TrazaEvaluacionStore(), overrides);
    }

    private ResultadoEvaluacion evaluar(HardwareCoverage hardware) {
        return orquestador.evaluate(RiegoCtx.a("10:05").humedad(40.0).inicioCiclo("10:00").hardware(hardware).build(),
                OrigenEvaluacion.TELEMETRIA);
    }

    private static List<RuleAction> de(ResultadoEvaluacion r, ActionType tipo) {
        return r.acciones().stream().filter(a -> a.type() == tipo).toList();
    }

    private static TrazaRegla regla(ResultadoEvaluacion r, String nombre) {
        return r.traza().reglas().stream().filter(t -> t.ruleId().equals(nombre)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("sector sin electroválvula: el riego se aborta con 'hardware incompleto', sin alerta; insumo y malla siguen")
    void sinElectrovalvula() {
        ResultadoEvaluacion r = evaluar(SIN_ELECTROVALVULA);

        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).isEmpty();
        assertThat(de(r, ActionType.ABORT_RIEGO)).extracting(RuleAction::ruleName).contains("IncompleteHardwareRule");
        assertThat(de(r, ActionType.ALERTA)).isEmpty();
        assertThat(regla(r, "RiegoPorDeficitRule").estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
        assertThat(regla(r, "RiegoPorDeficitRule").bloqueadaPor()).isEqualTo("IncompleteHardwareRule");
        assertThat(de(r, ActionType.ACTIVAR_BOMBA)).hasSize(1);
        assertThat(de(r, ActionType.MOVER_MEDIASOMBRA)).hasSize(1);
        assertThat(r.acciones()).extracting(RuleAction::motivo)
                .anyMatch(m -> m.toLowerCase().contains("hardware incompleto"));
    }

    @Test
    @DisplayName("sector sin bomba: el riego procede; sólo la rama de insumos se aborta")
    void sinBomba() {
        ResultadoEvaluacion r = evaluar(SIN_BOMBA);

        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).hasSize(1);
        assertThat(de(r, ActionType.ACTIVAR_BOMBA)).isEmpty();
        assertThat(regla(r, "Dosis").estado()).isEqualTo(EstadoRegla.OMITIDA_RAMA_BLOQUEADA);
        assertThat(regla(r, "Dosis").bloqueadaPor()).isEqualTo("IncompleteHardwareRule");
        assertThat(de(r, ActionType.MOVER_MEDIASOMBRA)).hasSize(1);
    }

    @Test
    @DisplayName("hardware completo: la regla registra 'completo' y la cadena sigue")
    void completo() {
        ResultadoEvaluacion r = evaluar(HardwareCoverage.COMPLETE);

        TrazaRegla hardware = regla(r, "IncompleteHardwareRule");
        assertThat(hardware.estado()).isEqualTo(EstadoRegla.EVALUADA);
        assertThat(hardware.acciones()).extracting(a -> a.tipo()).containsExactly(ActionType.NOOP_INFO);
        assertThat(hardware.acciones().get(0).motivo()).containsIgnoringCase("completo");
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).hasSize(1);
        assertThat(de(r, ActionType.ACTIVAR_BOMBA)).hasSize(1);
        assertThat(de(r, ActionType.MOVER_MEDIASOMBRA)).hasSize(1);
    }

    @Test
    @DisplayName("el orden de la traza es: bloqueo manual, hardware incompleto, frescura del sensor")
    void ordenDeLaTraza() {
        List<String> orden = evaluar(HardwareCoverage.COMPLETE).traza().reglas().stream().map(TrazaRegla::ruleId).toList();

        assertThat(orden.subList(0, 3)).containsExactly("ManualLockRule", "IncompleteHardwareRule", "StaleSensorRule");
    }

    @Test
    @DisplayName("un bloqueo manual gana: con ABORT_ALL la regla de hardware ni se alcanza")
    void elBloqueoManualGana() {
        ResultadoEvaluacion r = orquestador.evaluate(
                RiegoCtx.a("10:05").humedad(40.0).bloqueado().hardware(SIN_ELECTROVALVULA).build(), OrigenEvaluacion.TELEMETRIA);

        assertThat(regla(r, "IncompleteHardwareRule").estado()).isEqualTo(EstadoRegla.NO_ALCANZADA);
    }

    @Test
    @DisplayName("apagada desde el simulador: no actúa y el sector sin electroválvula se evalúa como si estuviera completo")
    void apagada() {
        overrides.turnOff("IncompleteHardwareRule");

        ResultadoEvaluacion r = evaluar(SIN_ELECTROVALVULA);

        assertThat(regla(r, "IncompleteHardwareRule").estado()).isEqualTo(EstadoRegla.OMITIDA_DESHABILITADA);
        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).hasSize(1);
    }

    @Test
    @DisplayName("bypass 'hardware': ignora los actuadores faltantes")
    void bypass() {
        overrides.put("IncompleteHardwareRule", new RuleOverride(Map.of(), Set.of("hardware")));

        ResultadoEvaluacion r = evaluar(HardwareCoverage.NONE);

        assertThat(de(r, ActionType.ACTIVAR_VALVULA)).hasSize(1);
        assertThat(de(r, ActionType.ACTIVAR_BOMBA)).hasSize(1);
        assertThat(de(r, ActionType.MOVER_MEDIASOMBRA)).hasSize(1);
    }
}
