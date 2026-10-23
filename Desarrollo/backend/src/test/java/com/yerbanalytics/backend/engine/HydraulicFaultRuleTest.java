package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.TestRuleOverrides;
import com.yerbanalytics.backend.engine.rules.HydraulicFaultRule;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-05: un sector con la electroválvula marcada como averiada sale del riego y de la dosificación autónomos,
 * y cancela el riego que ya estaba encolado. Los demás sectores no se enteran.
 */
@DisplayName("HydraulicFaultRule (S-05)")
class HydraulicFaultRuleTest {

    private static final HardwareFaults SIN_CAUDAL = new HardwareFaults("SIN_CAUDAL");

    private final HydraulicFaultRule rule = new HydraulicFaultRule();

    private static RuleContext ctx(HardwareFaults faults) {
        return RiegoCtx.a("10:05").humedad(40.0).faults(faults).build();
    }

    @Test
    @DisplayName("sin falla → NOOP_INFO y la cadena sigue, con la comparación 'cumple'")
    void sana() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(ctx(HardwareFaults.NONE), ev);

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(ev.comparaciones()).hasSize(1)
                .allSatisfy(c -> assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE));
    }

    @Test
    @DisplayName("falla hidráulica → corta el riego y la dosificación del sector, y retira el riego encolado")
    void conFalla() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(ctx(SIN_CAUDAL), ev);

        assertThat(acciones).extracting(RuleAction::type)
                .containsExactly(ActionType.ABORT_RIEGO, ActionType.ABORT_INSUMO);
        assertThat(acciones.get(0).detalle()).isEqualTo(CancelaRiego.TODAS);
        assertThat(acciones).allSatisfy(a -> assertThat(a.motivo())
                .contains("MZ-2-001").containsIgnoringCase("falla hidráulica").containsIgnoringCase("sin caudal"));
        assertThat(ev.comparaciones()).hasSize(1)
                .allSatisfy(c -> assertThat(c.resultado()).isEqualTo(ResultadoComparacion.NO_CUMPLE));
    }

    @Test
    @DisplayName("nunca emite ABORT_ALL ni ABORT_MEDIASOMBRA: la malla de la zona no es del sector")
    void sinAbortAllNiMalla() {
        assertThat(rule.evaluate(ctx(SIN_CAUDAL), ev(rule)))
                .extracting(RuleAction::type).doesNotContain(ActionType.ABORT_ALL, ActionType.ABORT_MEDIASOMBRA);
    }

    @Test
    @DisplayName("no emite alertas: la alerta sale una sola vez, cuando el nodo informa la falla")
    void sinAlertas() {
        assertThat(rule.evaluate(ctx(SIN_CAUDAL), ev(rule))).extracting(RuleAction::type).doesNotContain(ActionType.ALERTA);
    }

    @Test
    @DisplayName("un tipo de falla desconocido igual saca al sector (se muestra tal cual)")
    void tipoDesconocido() {
        List<RuleAction> acciones = rule.evaluate(ctx(new HardwareFaults("FALLA_RARA")), ev(rule));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ABORT_RIEGO, ActionType.ABORT_INSUMO);
        assertThat(acciones.get(0).motivo()).contains("FALLA_RARA");
    }

    @Test
    @DisplayName("bypass 'fault': el sector con la falla marcada se evalúa como si estuviera sano")
    void bypassFault() {
        TestRuleOverrides store = new TestRuleOverrides();
        store.put(HydraulicFaultRule.NAME, new RuleOverride(true, Map.of(), Set.of("fault")));

        assertThat(rule.evaluate(ctx(SIN_CAUDAL).withOverrides(store), ev(rule)))
                .extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("metadatos: segunda regla de seguridad, antes de las de riego; rama global; controlable desde el simulador")
    void metadatos() {
        assertThat(rule).isInstanceOf(Rule.class).isInstanceOf(DemoControllable.class);
        assertThat(rule.name()).isEqualTo("HydraulicFaultRule");
        assertThat(rule.priority()).isEqualTo(2);
        assertThat(rule.branch()).isEqualTo(RuleBranch.GLOBAL);
        assertThat(rule.label()).isNotBlank().isNotEqualTo(rule.name());
        assertThat(rule.bypassKeys()).containsExactly("fault");
        assertThat(rule.cooldownKeys()).isEmpty();
        assertThat(rule.parametros()).isEmpty();
        assertThat(rule.usaDiagnostico()).isFalse();
    }
}
