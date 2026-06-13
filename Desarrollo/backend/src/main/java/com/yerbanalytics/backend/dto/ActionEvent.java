package com.yerbanalytics.backend.dto;

public record ActionEvent(
        String title, String detail, String time, String result,
        String resSoft, String resInk, String tint, String ink, String path
) {}