package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import com.yerbanalytics.backend.engine.rules.ShadeFaultRule;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static org.assertj.core.api.Assertions.assertThat;

/** S-05: con la mediasombra de la macro-zona averiada, la rama de la malla no recibe comandos nuevos. */
@DisplayName("ShadeFaultRule (S-05)")
class ShadeFaultRuleTest {

    private static final HardwareFaults MALLA = new HardwareFaults(null, "MEDIASOMBRA_FIN_DE_CARRERA");

    private final ShadeFaultRule rule = new ShadeFaultRule();

    private static RuleContext ctx(HardwareFaults faults) {
        return RiegoCtx.a("10:05").humedad(40.0).faults(faults).build();
    }

    @Test
    @DisplayName("sin falla → NOOP_INFO y la rama de la malla sigue")
    void sana() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(ctx(HardwareFaults.NONE), ev);

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(ev.comparaciones()).hasSize(1)
                .allSatisfy(c -> assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE));
    }

    @Test
    @DisplayName("falla de la malla → ABORT_MEDIASOMBRA con el motivo, sin tocar riego ni insumos")
    void conFalla() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(ctx(MALLA), ev);

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ABORT_MEDIASOMBRA);
        assertThat(acciones.get(0).motivo()).containsIgnoringCase("falla mecánica").containsIgnoringCase("mediasombra");
        assertThat(ev.comparaciones()).allSatisfy(c -> assertThat(c.resultado()).isEqualTo(ResultadoComparacion.NO_CUMPLE));
    }

    @Test
    @DisplayName("una falla hidráulica del sector no frena la malla de la zona")
    void lasHidraulicasNoAfectanALaMalla() {
        assertThat(rule.evaluate(ctx(new HardwareFaults("SIN_CAUDAL", null)), ev(rule)))
                .extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("bypass 'fault': la malla averiada se evalúa como si estuviera sana")
    void bypassFault() {
        RuleOverridesStore store = new RuleOverridesStore();
        store.put(ShadeFaultRule.NAME, new RuleOverride(true, Map.of(), Set.of("fault")));

        assertThat(rule.evaluate(ctx(MALLA).withOverrides(store), ev(rule)))
                .extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("metadatos: rama de la mediasombra, justo antes de la regla que mueve la malla")
    void metadatos() {
        assertThat(rule).isInstanceOf(Rule.class).isInstanceOf(DemoControllable.class);
        assertThat(rule.name()).isEqualTo("ShadeFaultRule");
        assertThat(rule.priority()).isEqualTo(13);
        assertThat(rule.branch()).isEqualTo(RuleBranch.MEDIASOMBRA);
        assertThat(rule.label()).isNotBlank().isNotEqualTo(rule.name());
        assertThat(rule.bypassKeys()).containsExactly("fault");
        assertThat(rule.cooldownKeys()).isEmpty();
        assertThat(rule.parametros()).isEmpty();
    }
}
