package com.yerbanalytics.backend.dto;

import java.util.List;
import java.util.Map;

public record NurseryData(
        List<Zona> zonas, List<Sector> sectors, Map<String, Sector> byId,
        Stats stats, List<PriorityItem> priority, List<DiagnosisCard> diagnoses,
        Map<String, DiagnosisCard> diagById, List<DiagnosisCard> recentDiag,
        List<ActionEvent> actions, List<Alert> alerts, Weather weather,
        Map<String, ColorPair> sevMap, Map<String, String> tints, List<MetricSpec> specs
) {}