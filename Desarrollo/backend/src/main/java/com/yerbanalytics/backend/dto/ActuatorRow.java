package com.yerbanalytics.backend.dto;

public record ActuatorRow(
        String name, String state, boolean active, String path, String soft, String ink, String dot
) {}