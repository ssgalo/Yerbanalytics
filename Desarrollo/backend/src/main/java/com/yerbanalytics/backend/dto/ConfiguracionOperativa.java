package com.yerbanalytics.backend.dto;

/** Límites operativos de actuadores y parámetros de seguimiento (HU-15). */
public record ConfiguracionOperativa(
        double riegoTiempoMaxSeg,
        double riegoVolMaxDiarioMl,
        double insumoDosisMax24hMl,
        double mediasombraAperturaMaxPct,
        int seguimientoLatenciaMin,
        double seguimientoDeltaMin,
        String updatedBy,
        Long updatedTs
) {}
