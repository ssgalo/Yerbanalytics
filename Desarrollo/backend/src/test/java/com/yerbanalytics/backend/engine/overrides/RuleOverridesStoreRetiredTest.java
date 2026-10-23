package com.yerbanalytics.backend.engine.overrides;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RuleOverridesStore · reglas retiradas")
class RuleOverridesStoreRetiredTest {

    @Test
    void withoutRetiredRulesEverythingIsOn() {
        RuleOverridesStore store = new RuleOverridesStore();

        assertThat(store.isEnabled("SupplyRule")).isTrue();
        assertThat(store.snapshot()).isEmpty();
    }

    @Test
    void retiredRulesStartOffAndTheRestStaysOn() {
        RuleOverridesStore store = new RuleOverridesStore(List.of("SupplyRule", " DailyDoseLimitRule ", ""));

        assertThat(store.isEnabled("SupplyRule")).isFalse();
        assertThat(store.isEnabled("DailyDoseLimitRule")).isFalse();
        assertThat(store.isEnabled("RiegoPorDeficitRule")).isTrue();
    }

    @Test
    void theDemoCanTurnARetiredRuleBackOn() {
        RuleOverridesStore store = new RuleOverridesStore(List.of("SupplyRule"));

        store.put("SupplyRule", RuleOverride.NONE);

        assertThat(store.isEnabled("SupplyRule")).isTrue();
    }

    @Test
    void clearingLeavesTheRetiredRulesOff() {
        RuleOverridesStore store = new RuleOverridesStore(List.of("SupplyRule"));
        store.put("SupplyRule", RuleOverride.NONE);

        store.clear();

        assertThat(store.isEnabled("SupplyRule")).isFalse();
    }
}
