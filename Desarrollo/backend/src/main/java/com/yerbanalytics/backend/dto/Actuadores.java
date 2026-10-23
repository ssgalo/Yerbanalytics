package com.yerbanalytics.backend.dto;

/** Actuadores del sector. La mediasombra no está acá: es de la macro-zona ({@link Zona#mediasombra()}). */
public record Actuadores(String valve, String pump) {
}