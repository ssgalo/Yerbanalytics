package com.yerbanalytics.backend.engine.overrides;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory overrides of the rules. Nothing in production writes to it: the only writer is the demo control, which
 * exists only with {@code yerbanalytics.demo.enabled=true}. Nothing is persisted, so a restart goes back to the
 * configured behaviour.
 *
 * <p>It starts with the <b>retired rules</b> ({@code yerbanalytics.reglas.retiradas}) turned off: rules that stay in
 * the code but must not act, because what replaces them is not there yet. Today those are the v1 supply rules
 * ({@code SupplyRule}, {@code DailyDoseLimitRule}): the v2 application rules (N-*, F-*) decide through the
 * {@code ApplicationService} and will take over, and the v1 pair goes away with them. In production they stay off;
 * with the demo enabled the simulator can turn them back on, and {@link #clear()} leaves them off again.
 */
@Component
public class RuleOverridesStore implements RuleOverrides {

    private static final RuleOverride OFF = new RuleOverride(false, Map.of(), Set.of());

    private final Set<String> retired;
    private final Map<String, RuleOverride> overrides = new ConcurrentHashMap<>();

    public RuleOverridesStore() {
        this(List.of());
    }

    @Autowired
    public RuleOverridesStore(
            @Value("${yerbanalytics.reglas.retiradas:SupplyRule,DailyDoseLimitRule}") List<String> retired) {
        this.retired = retired.stream().map(String::trim).filter(n -> !n.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        seedRetired();
    }

    private void seedRetired() {
        retired.forEach(name -> overrides.put(name, OFF));
    }

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

    /** Back to the configured behaviour for every rule (the retired ones stay off). */
    public void clear() {
        overrides.clear();
        seedRetired();
    }

    /** Snapshot of the rules that have an override. */
    public Map<String, RuleOverride> snapshot() {
        return Map.copyOf(overrides);
    }
}
