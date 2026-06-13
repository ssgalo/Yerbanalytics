package com.yerbanalytics.backend.dto;

public record DiagnosisCard(
        String id, String sectorId, String zonaName, String estado, Double conf,
        String sev, String sevSoft, String sevInk, String thumb, String time, boolean concluyente
) {}