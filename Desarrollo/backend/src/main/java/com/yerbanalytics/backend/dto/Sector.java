package com.yerbanalytics.backend.dto;

import java.util.List;

public record Sector(
        String id, String zona, String zonaName, Integer n,
        String status, String color, String statusLabel, String tip,
        List<Metric> metrics, Diagnosis diagnosis, String reason,
        Actuadores actuadores, String ago, boolean stale
) {}