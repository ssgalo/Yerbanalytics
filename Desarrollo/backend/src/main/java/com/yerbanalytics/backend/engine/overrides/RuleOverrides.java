package com.yerbanalytics.backend.engine.overrides;

import java.util.Optional;

/**
 * Read-only view of the overrides that rules and the irrigation dispatch consult. With {@link #NONE} (production)
 * every rule is on, keeps its configured cooldowns and applies all its guards.
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

    /** {@code false} if the rule was turned off. */
    boolean isEnabled(String ruleName);

    /** Cooldown (minutes) that replaces the configured one for {@code key}, if any. {@code 0} means no cooldown. */
    Optional<Double> cooldownMinutes(String ruleName, String key);

    /** {@code true} if the guard {@code key} of the rule is not applied. */
    boolean isBypassed(String ruleName, String key);

    /**
     * {@code true} if a guard must not be applied by a component that re-checks it on behalf of the rule (the
     * irrigation dispatch): either the guard was bypassed or the whole rule is off.
     */
    default boolean skipsGuard(String ruleName, String key) {
        return !isEnabled(ruleName) || isBypassed(ruleName, key);
    }
}
