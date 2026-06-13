package com.yerbanalytics.backend.dto;

public record MetricTile(
        String label, String value, String unit, String status,
        String color, String line, String ideal, String soft
) {}