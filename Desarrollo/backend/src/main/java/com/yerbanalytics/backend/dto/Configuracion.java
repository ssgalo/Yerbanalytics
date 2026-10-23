package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Configuración agronómica completa (HU-15): umbrales de métricas y límites operativos (con el horario por defecto de
 * la mediasombra). Espejo exacto del tipo {@code Configuracion} del frontend. La rustificación ya no vive acá: es un plan
 * por macro-zona ({@code /api/zonas/{zonaId}/rustificacion}).
 */
public record Configuracion(
        List<UmbralMetrica> umbrales,
        ConfiguracionOperativa operativa
) {}
