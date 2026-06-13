package com.yerbanalytics.backend.dto;

public record Alert(
        String level, String color, String time,
        String sectorId, String msg, boolean read
) {}