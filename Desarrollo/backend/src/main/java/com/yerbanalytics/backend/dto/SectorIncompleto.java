package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Sector con mapeo de hardware incompleto (HU-18 CA-04): tiene algún dispositivo pero le
 * faltan actuadores requeridos. La actuación autónoma queda deshabilitada en él.
 */
public record SectorIncompleto(
        String sectorId,
        String zonaName,
        List<String> faltantes
) {}
