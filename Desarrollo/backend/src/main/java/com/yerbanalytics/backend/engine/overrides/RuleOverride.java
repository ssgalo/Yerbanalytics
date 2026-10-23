package com.yerbanalytics.backend.engine.overrides;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runtime override of a single rule: whether it runs, the cooldowns it applies and the guards it skips.
 *
 * <p>A rule may declare several cooldowns and several bypassable guards; each one is identified by a key that the
 * rule itself publishes (see the {@code COOLDOWN_*} and {@code BYPASS_*} constants of each rule).
 *
 * @param enabled         explicit demo switch: {@code false} turns the rule off, {@code true} forces it on even if the
 *                        vivero turned it off, {@code null} (never touched) leaves the operational state in charge
 * @param cooldownMinutes cooldown replacing the configured one, by key. {@code 0} removes that cooldown
 * @param bypassed        keys of the guards that are not applied
 */
public record RuleOverride(Boolean enabled, Map<String, Double> cooldownMinutes, Set<String> bypassed) {

    /** A rule with no override: its operational state, its configured cooldowns and all its guards. */
    public static final RuleOverride NONE = new RuleOverride(null, Map.of(), Set.of());

    public RuleOverride {
        cooldownMinutes = cooldownMinutes == null ? Map.of() : Map.copyOf(cooldownMinutes);
        bypassed = bypassed == null ? Set.of() : Set.copyOf(bypassed);
        cooldownMinutes.forEach((key, minutes) -> {
            if (minutes == null || minutes < 0 || minutes.isNaN() || minutes.isInfinite()) {
                throw new IllegalArgumentException("Cooldown '" + key + "' must be a number of minutes >= 0.");
            }
        });
    }

    /** The explicit demo switch, if there is one. Empty means the operational state decides. */
    public Optional<Boolean> explicitEnabled() {
        return Optional.ofNullable(enabled);
    }
}
