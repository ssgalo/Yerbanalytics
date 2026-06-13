package com.yerbanalytics.backend.dto;

public record DiagnosisDetail(
        String estado, Double conf, String sev, String sevSoft,
        String sevInk, String thumb, boolean concluyente, boolean hasFoto
) {}