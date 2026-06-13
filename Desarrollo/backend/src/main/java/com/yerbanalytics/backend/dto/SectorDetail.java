package com.yerbanalytics.backend.dto;

import java.util.List;

public record SectorDetail(
        String mainLine, String mainArea, String mainMin, String mainMax,
        List<MetricTile> metricTiles, DiagnosisDetail diag, List<ActuatorRow> actsRows,
        List<HistoryEntry> hist, Evolution evo, String statusSoft, String statusInk
) {}