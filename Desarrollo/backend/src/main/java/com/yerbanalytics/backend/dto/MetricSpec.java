package com.yerbanalytics.backend.dto;

public record MetricSpec(
        String key, String label, String unit, Double[] ideal,
        Double[] warn, Double[] crit, Integer dec, Integer base
) {}