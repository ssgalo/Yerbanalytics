package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.effectiveness.EffectivenessBlocks;
import com.yerbanalytics.backend.effectiveness.EffectivenessKind;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.rules.IneffectiveActionRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.evaluar;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("S-06: IneffectiveActionRule (riego sin efectividad)")
class IneffectiveActionRuleTest {

    private final IneffectiveActionRule rule = new IneffectiveActionRule();

    private static RuleContext ctx(EffectivenessBlocks blocks, RuleOverrides overrides) {
        return new RuleContext(RuleContextTestFactory.sectorBasico("MZ-1-045"), RuleContextTestFactory.zonaBasica("MZ-1"),
                List.of(), List.of(), RuleContextTestFactory.defaultConfig(), "ok", Instant.now(), null, false,
                ContextoRiego.vacio(), overrides, Set.of(), HardwareCoverage.COMPLETE, HardwareFaults.NONE, blocks);
    }

    private static final EffectivenessBlocks IRRIGATION_BLOCKED = new EffectivenessBlocks(true, Set.of());

    @Test
    void conElRiegoBloqueadoCortaLaRamaDeRiegoYRetiraLoQueEsperaSalvoElDeficitCritico() {
        List<RuleAction> actions = evaluar(rule, ctx(IRRIGATION_BLOCKED, RuleOverrides.NONE));

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).type()).isEqualTo(ActionType.ABORT_RIEGO);
        assertThat(actions.get(0).detalle()).isEqualTo(CancelaRiego.SOLO_DEFICIT_COMUN);
        assertThat(actions.get(0).motivo()).contains("S-06").contains("MZ-1").contains("revisión");
    }

    @Test
    void sinBloqueoNoHaceNada() {
        List<RuleAction> actions = evaluar(rule, ctx(EffectivenessBlocks.NONE, RuleOverrides.NONE));

        assertThat(actions).singleElement().satisfies(a -> assertThat(a.type()).isEqualTo(ActionType.NOOP_INFO));
    }

    @Test
    void elBloqueoDeUnTratamientoNoFrenaElRiego() {
        List<RuleAction> actions = evaluar(rule,
                ctx(new EffectivenessBlocks(false, Set.of(EffectivenessKind.FUNGICIDE)), RuleOverrides.NONE));

        assertThat(actions).singleElement().satisfies(a -> assertThat(a.type()).isEqualTo(ActionType.NOOP_INFO));
    }

    @Test
    void conElBypassBlockElRiegoSigue() {
        RuleOverrides bypass = new RuleOverrides() {
            @Override public boolean isEnabled(String ruleName) { return true; }
            @Override public Optional<Double> cooldownMinutes(String ruleName, String key) { return Optional.empty(); }
            @Override public boolean isBypassed(String ruleName, String key) {
                return IneffectiveActionRule.NAME.equals(ruleName) && IneffectiveActionRule.BYPASS_BLOCK.equals(key);
            }
        };

        List<RuleAction> actions = evaluar(rule, ctx(IRRIGATION_BLOCKED, bypass));

        assertThat(actions).singleElement().satisfies(a -> assertThat(a.type()).isEqualTo(ActionType.NOOP_INFO));
    }

    @Test
    void esUnaReglaDeRiegoDemoControllableQueCorreDespuesDeR02() {
        assertThat(rule).isInstanceOf(Rule.class).isInstanceOf(DemoControllable.class);
        assertThat(rule.name()).isEqualTo("IneffectiveActionRule");
        assertThat(rule.branch()).isEqualTo(RuleBranch.RIEGO);
        assertThat(rule.priority()).isEqualTo(7);
        assertThat(rule.bypassKeys()).containsExactly("block");
        assertThat(rule.parametros()).isEmpty();
    }
}
