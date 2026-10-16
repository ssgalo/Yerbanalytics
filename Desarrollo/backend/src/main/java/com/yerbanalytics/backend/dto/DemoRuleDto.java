package com.yerbanalytics.backend.dto;

import java.util.Map;
import java.util.Set;

/**
 * State of a rule as exposed by {@code GET /api/demo/rules}.
 *
 * @param name           technical name of the rule
 * @param label          human-readable label
 * @param branch         the rule's branch (GLOBAL, RIEGO, INSUMO, …)
 * @param priority       evaluation order (lower = higher priority)
 * @param enabled        whether the rule runs ({@code false} → OMITIDA_DESHABILITADA in the trace)
 * @param cooldownKeys   cooldown keys this rule declares (one input per key in the simulator UI)
 * @param bypassKeys     bypass keys this rule declares (one toggle per key in the simulator UI)
 * @param cooldownMinutes current cooldown overrides by key (empty in production)
 * @param bypassed       bypass keys currently active (empty in production)
 */
public record DemoRuleDto(
        String name,
        String label,
        String branch,
        int priority,
        boolean enabled,
        Set<String> cooldownKeys,
        Set<String> bypassKeys,
        Map<String, Double> cooldownMinutes,
        Set<String> bypassed
) {}
