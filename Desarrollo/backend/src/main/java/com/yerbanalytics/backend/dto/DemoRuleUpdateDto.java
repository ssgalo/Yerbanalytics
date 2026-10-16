package com.yerbanalytics.backend.dto;

import java.util.Map;
import java.util.Set;

/**
 * Body of {@code PUT /api/demo/rules/{ruleName}}.
 *
 * <p>All fields are optional — only the ones present are applied.
 *
 * @param enabled        turn the rule on or off; {@code null} = keep current
 * @param cooldownMinutes replacement cooldown map; {@code null} = keep current
 * @param bypassed       replacement bypass key set; {@code null} = keep current
 */
public record DemoRuleUpdateDto(
        Boolean enabled,
        Map<String, Double> cooldownMinutes,
        Set<String> bypassed
) {}
