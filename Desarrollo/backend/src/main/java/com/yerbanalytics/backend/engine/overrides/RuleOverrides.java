package com.yerbanalytics.backend.engine.overrides;

import java.util.Optional;
import java.util.Set;

/**
 * Read-only view of the effective state that rules, services and the irrigation dispatch consult: whether a piece runs
 * and, in demo mode, its cooldowns and bypassed guards. The only implementation is {@link EffectiveRuleOverrides}; with
 * {@link #NONE} (unit tests) every rule is on, keeps its configured cooldowns and applies all its guards.
 */
public interface RuleOverrides {

    /** No overrides at all. */
    RuleOverrides NONE = new RuleOverrides() {
        @Override
        public boolean isEnabled(String ruleName) {
            return true;
        }

        @Override
        public Optional<Double> cooldownMinutes(String ruleName, String key) {
            return Optional.empty();
        }

        @Override
        public boolean isBypassed(String ruleName, String key) {
            return false;
        }
    };

    /** {@code false} if the rule is off (operational state, or the explicit demo override when it exists). */
    boolean isEnabled(String ruleName);

    /** Cooldown (minutes) that replaces the configured one for {@code key}, if any. {@code 0} means no cooldown. */
    Optional<Double> cooldownMinutes(String ruleName, String key);

    /** {@code true} if the guard {@code key} of the rule is not applied. */
    boolean isBypassed(String ruleName, String key);

    /**
     * Names of the pieces that are off right now (the effective state). The history stores them with each engine
     * decision so its graph draws what ran at the time. Empty by default.
     */
    default Set<String> inactiveNames() {
        return Set.of();
    }

    /**
     * {@code true} if a guard must not be applied by a component that re-checks it on behalf of the rule (the
     * irrigation dispatch): either the guard was bypassed or the whole rule is off.
     */
    default boolean skipsGuard(String ruleName, String key) {
        return !isEnabled(ruleName) || isBypassed(ruleName, key);
    }
}
