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
 * @param enabled        the demo layer's switch: {@code false} only if the simulator turned the rule off; {@code true}
 *                       otherwise (also when it never touched it). It is not what decides on its own: see
 *                       {@code effectiveEnabled}
 * @param operationalActive what the vivero decided (read only: the simulator never writes it). {@code false} means the
 *                       rule is off in the vivero, so an {@code enabled: true} here is the demo forcing it on
 * @param effectiveEnabled whether the rule actually runs ({@code false} → OMITIDA_DESHABILITADA in the trace): the
 *                       explicit demo override if there is one, the operational state if not
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
        boolean operationalActive,
        boolean effectiveEnabled,
        Set<String> cooldownKeys,
        Set<String> bypassKeys,
        Map<String, Double> cooldownMinutes,
        Set<String> bypassed
) {}
