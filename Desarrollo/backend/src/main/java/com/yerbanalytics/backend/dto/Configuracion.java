package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Configuración agronómica completa (HU-15): umbrales de métricas, límites operativos
 * y plan de rustificación. Espejo exacto del tipo {@code Configuracion} del frontend.
 */
public record Configuracion(
        List<UmbralMetrica> umbrales,
        ConfiguracionOperativa operativa,
        List<RustificacionEtapa> rustificacion
) {}
