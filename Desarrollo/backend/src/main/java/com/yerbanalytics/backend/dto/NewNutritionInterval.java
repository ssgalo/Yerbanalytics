package com.yerbanalytics.backend.dto;

/** Cuerpo de {@code PUT /api/zonas/{zonaId}/plan-nutricion}: el intervalo del plan, en días enteros. */
public record NewNutritionInterval(Integer intervalDays) {}
