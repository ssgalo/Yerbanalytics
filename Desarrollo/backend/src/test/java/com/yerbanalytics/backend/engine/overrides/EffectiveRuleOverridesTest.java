package com.yerbanalytics.backend.engine.overrides;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EffectiveRuleOverrides")
class EffectiveRuleOverridesTest {

    private static final String RULE = "RiegoPorDeficitRule";

    private final RuleStateRepository repository = mock(RuleStateRepository.class);
    private final RuleOverridesStore demo = new RuleOverridesStore();

    private EffectiveRuleOverrides efectivo(boolean operativa) {
        when(repository.findAll()).thenReturn(List.of(new RuleStateEntity(RULE, operativa, null, "Ana", 1L)));
        return new EffectiveRuleOverrides(demo, new OperationalRuleStates(repository, List.of()));
    }

    /** El estado efectivo es el operativo: la capa de demo no tiene interruptor. */
    @ParameterizedTest(name = "operativo={0} -> efectivo={0}")
    @CsvSource({"true", "false"})
    void elEfectivoEsElOperativo(boolean operativo) {
        demo.put(RULE, new RuleOverride(Map.of("k", 0.0), Set.of("g")));

        assertThat(efectivo(operativo).isEnabled(RULE)).isEqualTo(operativo);
    }

    @Test
    @DisplayName("cooldowns y bypass salen de la capa de demo, sin tocar el operativo")
    void cooldownsYBypassSalenDeLaDemo() {
        demo.put(RULE, new RuleOverride(Map.of("k", 3.0), Set.of("g")));
        EffectiveRuleOverrides efectivo = efectivo(false);

        assertThat(efectivo.cooldownMinutes(RULE, "k")).contains(3.0);
        assertThat(efectivo.cooldownMinutes(RULE, "otro")).isEmpty();
        assertThat(efectivo.isBypassed(RULE, "g")).isTrue();
        assertThat(efectivo.isBypassed(RULE, "otro")).isFalse();
    }

    @Test
    @DisplayName("skipsGuard: una regla apagada (efectiva) saltea sus guardas")
    void skipsGuardConLaReglaApagada() {
        assertThat(efectivo(false).skipsGuard(RULE, "g")).isTrue();
        assertThat(efectivo(true).skipsGuard(RULE, "g")).isFalse();
    }

    @Test
    @DisplayName("clear de la demo no cambia el estado operativo")
    void clearNoCambiaElOperativo() {
        demo.put(RULE, new RuleOverride(Map.of("k", 0.0), Set.of()));
        EffectiveRuleOverrides efectivo = efectivo(false);

        demo.clear();

        assertThat(efectivo.isEnabled(RULE)).isFalse();
        assertThat(efectivo.cooldownMinutes(RULE, "k")).isEmpty();
    }

    @Test
    @DisplayName("los nombres apagados son los del estado operativo")
    void nombresApagados() {
        when(repository.findAll()).thenReturn(List.of(
                new RuleStateEntity("A", false, null, "Ana", 1L), new RuleStateEntity("B", true, null, "Ana", 1L)));
        EffectiveRuleOverrides efectivo = new EffectiveRuleOverrides(demo, new OperationalRuleStates(repository, List.of()));
        demo.put("B", new RuleOverride(Map.of("k", 0.0), Set.of()));

        assertThat(efectivo.inactiveNames()).containsExactly("A");
    }
}
