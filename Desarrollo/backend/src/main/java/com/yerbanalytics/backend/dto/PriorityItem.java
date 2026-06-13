package com.yerbanalytics.backend.dto;

public record PriorityItem(
        String id, String color, String reason, String sev,
        String sevSoft, String sevInk, String pulse
) {}