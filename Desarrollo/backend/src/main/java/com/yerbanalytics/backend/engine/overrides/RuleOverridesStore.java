package com.yerbanalytics.backend.engine.overrides;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory overrides of the rules. It starts empty and nothing in production writes to it: the only writer is
 * {@code RuleOverridesController}, which exists only when {@code yerbanalytics.engine.overrides-enabled=true}.
 * Nothing is persisted, so a restart goes back to the configured behaviour.
 */
@Component
public class RuleOverridesStore implements RuleOverrides {

    private final Map<String, RuleOverride> overrides = new ConcurrentHashMap<>();

    @Override
    public boolean isEnabled(String ruleName) {
        return get(ruleName).enabled();
    }

    @Override
    public Optional<Double> cooldownMinutes(String ruleName, String key) {
        return Optional.ofNullable(get(ruleName).cooldownMinutes().get(key));
    }

    @Override
    public boolean isBypassed(String ruleName, String key) {
        return get(ruleName).bypassed().contains(key);
    }

    /** The override of a rule, or {@link RuleOverride#NONE}. */
    public RuleOverride get(String ruleName) {
        return overrides.getOrDefault(ruleName, RuleOverride.NONE);
    }

    /** Replaces the override of a rule. */
    public void put(String ruleName, RuleOverride override) {
        overrides.put(ruleName, override);
    }

    /** Back to the configured behaviour for every rule. */
    public void clear() {
        overrides.clear();
    }

    /** Snapshot of the rules that have an override. */
    public Map<String, RuleOverride> snapshot() {
        return Map.copyOf(overrides);
    }
}
