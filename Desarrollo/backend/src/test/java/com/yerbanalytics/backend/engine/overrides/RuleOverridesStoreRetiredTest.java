package com.yerbanalytics.backend.engine.overrides;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** La siembra de las retiradas dejó de ser de la capa de demo: ahora es el estado operativo por defecto. */
@DisplayName("RuleOverridesStore · reglas retiradas")
class RuleOverridesStoreRetiredTest {

    private static EffectiveRuleOverrides efectivo(RuleOverridesStore store, String... retiradas) {
        RuleStateRepository repository = mock(RuleStateRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        return new EffectiveRuleOverrides(store, new OperationalRuleStates(repository, List.of(retiradas)));
    }

    @Test
    void theStoreNoLongerSeedsAnything() {
        RuleOverridesStore store = new RuleOverridesStore();

        assertThat(store.snapshot()).isEmpty();
    }

    @Test
    void retiredRulesComeOutOffFromTheOperationalState() {
        EffectiveRuleOverrides efectivo = efectivo(new RuleOverridesStore(), "SupplyRule", "DailyDoseLimitRule");

        assertThat(efectivo.isEnabled("SupplyRule")).isFalse();
        assertThat(efectivo.isEnabled("DailyDoseLimitRule")).isFalse();
        assertThat(efectivo.isEnabled("RiegoPorDeficitRule")).isTrue();
    }

    @Test
    void theDemoLayerCannotTurnARetiredRuleBackOn() {
        RuleOverridesStore store = new RuleOverridesStore();
        EffectiveRuleOverrides efectivo = efectivo(store, "SupplyRule");

        store.put("SupplyRule", new RuleOverride(Map.of("k", 0.0), Set.of("g")));

        assertThat(efectivo.isEnabled("SupplyRule")).isFalse();
    }

    @Test
    void clearingTheDemoLayerDoesNotChangeTheOperationalState() {
        RuleOverridesStore store = new RuleOverridesStore();
        EffectiveRuleOverrides efectivo = efectivo(store, "SupplyRule");
        store.put("SupplyRule", new RuleOverride(Map.of("k", 0.0), Set.of()));

        store.clear();

        assertThat(efectivo.isEnabled("SupplyRule")).isFalse();
    }
}
