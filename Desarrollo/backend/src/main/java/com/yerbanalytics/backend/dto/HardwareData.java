package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Estado técnico de la flota de hardware (HU-18 / HU-21): dispositivos con su estado
 * derivado, KPIs agregados y sectores con mapeo incompleto. Espejo del tipo
 * {@code HardwareData} del frontend.
 */
public record HardwareData(
        List<Dispositivo> dispositivos,
        int total,
        int operativos,
        int bateriaBaja,
        int fueraDeServicio,
        int averiados,
        List<SectorIncompleto> incompletos
) {}
