package com.yerbanalytics.backend.dto;

/** Quién, cuándo y por qué prendió o apagó una regla por última vez ({@code updatedTs} en epoch ms). */
public record UltimoCambioReglaDto(String motivo, String updatedBy, Long updatedTs) {}
