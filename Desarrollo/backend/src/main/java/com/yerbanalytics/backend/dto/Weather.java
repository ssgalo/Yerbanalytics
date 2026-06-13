package com.yerbanalytics.backend.dto;

import java.util.List;

public record Weather(
        Double tempC, String cond, Double hum, Double uv,
        String uvLabel, String rainText, List<ForecastSlot> forecast
) {}