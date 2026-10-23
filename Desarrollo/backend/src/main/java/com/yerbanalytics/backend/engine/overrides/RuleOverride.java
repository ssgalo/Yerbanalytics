package com.yerbanalytics.backend.engine.overrides;

import java.util.Map;
import java.util.Set;

/**
 * Demo override of a single rule: the cooldowns it applies and the guards it skips. Whether the rule runs is not
 * here: the single source of truth for on/off is the operational state (Motor de reglas).
 *
 * <p>A rule may declare several cooldowns and several bypassable guards; each one is identified by a key that the
 * rule itself publishes (see the {@code COOLDOWN_*} and {@code BYPASS_*} constants of each rule).
 *
 * @param cooldownMinutes cooldown replacing the configured one, by key. {@code 0} removes that cooldown
 * @param bypassed        keys of the guards that are not applied
 */
public record RuleOverride(Map<String, Double> cooldownMinutes, Set<String> bypassed) {

    /** A rule with no override: its configured cooldowns and all its guards. */
    public static final RuleOverride NONE = new RuleOverride(Map.of(), Set.of());

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
