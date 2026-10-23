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

    /** El override explícito de demo gana; sin override rige el operativo. */
    @ParameterizedTest(name = "operativo={0}, demo={1} -> efectivo={2}")
    @CsvSource({
            "true,  ninguno, true",
            "true,  prendido, true",
            "true,  apagado, false",
            "false, ninguno, false",
            "false, prendido, true",
            "false, apagado, false"})
    void tablaOperativoPorDemo(boolean operativo, String override, boolean esperado) {
        switch (override) {
            case "prendido" -> demo.put(RULE, new RuleOverride(true, Map.of(), Set.of()));
            case "apagado" -> demo.put(RULE, new RuleOverride(false, Map.of(), Set.of()));
            default -> { }
        }

        assertThat(efectivo(operativo).isEnabled(RULE)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("un override de demo sólo de cooldowns o bypass no decide el estado")
    void cooldownsNoForzanElEstado() {
        demo.put(RULE, new RuleOverride(null, Map.of("k", 0.0), Set.of("g")));

        assertThat(efectivo(false).isEnabled(RULE)).isFalse();
        assertThat(efectivo(true).isEnabled(RULE)).isTrue();
    }

    @Test
    @DisplayName("cooldowns y bypass salen de la capa de demo, sin tocar el operativo")
    void cooldownsYBypassSalenDeLaDemo() {
        demo.put(RULE, new RuleOverride(null, Map.of("k", 3.0), Set.of("g")));
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
    @DisplayName("clear de la demo devuelve el estado operativo")
    void clearDevuelveElOperativo() {
        demo.put(RULE, new RuleOverride(true, Map.of(), Set.of()));
        EffectiveRuleOverrides efectivo = efectivo(false);
        assertThat(efectivo.isEnabled(RULE)).isTrue();

        demo.clear();

        assertThat(efectivo.isEnabled(RULE)).isFalse();
    }

    @Test
    @DisplayName("los nombres apagados efectivos: el override de demo prende y apaga")
    void nombresApagadosEfectivos() {
        when(repository.findAll()).thenReturn(List.of(
                new RuleStateEntity("A", false, null, "Ana", 1L), new RuleStateEntity("B", true, null, "Ana", 1L)));
        EffectiveRuleOverrides efectivo = new EffectiveRuleOverrides(demo, new OperationalRuleStates(repository, List.of()));
        demo.put("A", new RuleOverride(true, Map.of(), Set.of()));
        demo.put("B", new RuleOverride(false, Map.of(), Set.of()));
        demo.put("C", new RuleOverride(false, Map.of(), Set.of()));

        assertThat(efectivo.inactiveNames()).containsExactlyInAnyOrder("B", "C");
    }

    @Test
    @DisplayName("informa el estado operativo aparte del efectivo")
    void operativoAparte() {
        demo.put(RULE, new RuleOverride(true, Map.of(), Set.of()));
        EffectiveRuleOverrides efectivo = efectivo(false);

        assertThat(efectivo.isOperationallyActive(RULE)).isFalse();
        assertThat(efectivo.isEnabled(RULE)).isTrue();
    }
}
