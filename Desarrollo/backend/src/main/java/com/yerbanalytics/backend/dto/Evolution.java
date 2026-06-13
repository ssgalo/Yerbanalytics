package com.yerbanalytics.backend.dto;

public record Evolution(
        boolean show, String metric, String antes, String ahora, String unit,
        String delta, String latencia, String verdict, String vSoft, String vInk
) {}