package com.yerbanalytics.backend.dto;

public record Metric(
        String key, String label, String unit, Double raw,
        String value, String status, String color, MetricSpec spec
) {}