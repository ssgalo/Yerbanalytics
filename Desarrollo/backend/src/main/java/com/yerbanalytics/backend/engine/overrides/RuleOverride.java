package com.yerbanalytics.backend.engine.overrides;

import java.util.Map;
import java.util.Set;

/**
 * Runtime override of a single rule: whether it runs, the cooldowns it applies and the guards it skips.
 *
 * <p>A rule may declare several cooldowns and several bypassable guards; each one is identified by a key that the
 * rule itself publishes (see the {@code COOLDOWN_*} and {@code BYPASS_*} constants of each rule).
 *
 * @param enabled         {@code false} turns the rule off: the orchestrator skips it
 * @param cooldownMinutes cooldown replacing the configured one, by key. {@code 0} removes that cooldown
 * @param bypassed        keys of the guards that are not applied
 */
public record RuleOverride(boolean enabled, Map<String, Double> cooldownMinutes, Set<String> bypassed) {

    /** A rule with no override: on, with its configured cooldowns and all its guards. */
    public static final RuleOverride NONE = new RuleOverride(true, Map.of(), Set.of());

    public RuleOverride {
        cooldownMinutes = cooldownMinutes == null ? Map.of() : Map.copyOf(cooldownMinutes);
        bypassed = bypassed == null ? Set.of() : Set.copyOf(bypassed);
        cooldownMinutes.forEach((key, minutes) -> {
            if (minutes == null || minutes < 0 || minutes.isNaN() || minutes.isInfinite()) {
                throw new IllegalArgumentException("Cooldown '" + key + "' must be a number of minutes >= 0.");
            }
        });
    }
}
