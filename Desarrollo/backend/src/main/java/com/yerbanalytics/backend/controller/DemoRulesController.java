package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.DemoRuleDto;
import com.yerbanalytics.backend.dto.DemoRuleUpdateDto;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.RuleOverridesStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demo-only REST API for runtime rule control.
 *
 * <p>Only registered when {@code yerbanalytics.demo.enabled=true}; in production the routes
 * do not exist (Spring never registers this bean and they return 404).
 *
 * <p>Writes go to {@link RuleOverridesStore} — in-memory, never persisted. A restart resets
 * every rule to its production behaviour.
 */
@RestController
@RequestMapping("/api/demo/rules")
@ConditionalOnProperty(name = "yerbanalytics.demo.enabled", havingValue = "true")
public class DemoRulesController {

    private final RuleOrchestrator orchestrator;
    private final RuleOverridesStore store;

    public DemoRulesController(RuleOrchestrator orchestrator, RuleOverridesStore store) {
        this.orchestrator = orchestrator;
        this.store = store;
    }

    /** Lists all rules with their current override state. */
    @GetMapping
    public List<DemoRuleDto> list() {
        return orchestrator.getRules().stream().map(this::toDto).toList();
    }

    /** Updates the enabled flag, cooldown overrides and/or bypass set of a single rule. */
    @PutMapping("/{ruleName}")
    public ResponseEntity<DemoRuleDto> update(
            @PathVariable String ruleName,
            @RequestBody DemoRuleUpdateDto body) {

        Rule rule = orchestrator.getRules().stream()
                .filter(r -> r.name().equals(ruleName))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            return ResponseEntity.notFound().build();
        }

        RuleOverride current = store.get(ruleName);
        boolean enabled = body.enabled() != null ? body.enabled() : current.enabled();
        Map<String, Double> cooldowns = body.cooldownMinutes() != null
                ? body.cooldownMinutes() : current.cooldownMinutes();
        Set<String> bypassed = body.bypassed() != null ? body.bypassed() : current.bypassed();

        store.put(ruleName, new RuleOverride(enabled, cooldowns, bypassed));
        return ResponseEntity.ok(toDto(rule));
    }

    /** Shortcut: sets every rule's cooldown overrides to 0 (no wait between consecutive evaluations). */
    @PutMapping("/cooldowns/zero")
    public List<DemoRuleDto> zeroCooldowns() {
        for (Rule rule : orchestrator.getRules()) {
            if (rule.cooldownKeys().isEmpty()) continue;
            RuleOverride current = store.get(rule.name());
            Map<String, Double> zeroed = new java.util.HashMap<>(current.cooldownMinutes());
            for (String key : rule.cooldownKeys()) {
                zeroed.put(key, 0.0);
            }
            store.put(rule.name(), new RuleOverride(current.enabled(), zeroed, current.bypassed()));
        }
        return orchestrator.getRules().stream().map(this::toDto).toList();
    }

    private DemoRuleDto toDto(Rule rule) {
        RuleOverride override = store.get(rule.name());
        return new DemoRuleDto(
                rule.name(),
                rule.label(),
                rule.branch().name(),
                rule.priority(),
                override.enabled(),
                rule.cooldownKeys(),
                rule.bypassKeys(),
                override.cooldownMinutes(),
                override.bypassed());
    }
}
