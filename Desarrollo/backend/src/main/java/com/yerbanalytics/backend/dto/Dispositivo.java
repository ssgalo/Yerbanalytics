package com.yerbanalytics.backend.dto;

/**
 * Vista de un dispositivo en el panel técnico (HU-21). El estado, las etiquetas y los
 * colores son derivados por {@code HardwareService}. En el alta/recambio sólo se leen
 * {@code serial}, {@code tipo}, {@code zonaId} y {@code sectorId}. {@code falla} es el tipo de error
 * que informó el nodo (S-05) y {@code fallaDesde} el epoch ms en que se marcó. Espejo del tipo
 * {@code Dispositivo} del frontend.
 */
public record Dispositivo(
        String id,
        String serial,
        String tipo,
        String tipoLabel,
        String zonaId,
        String sectorId,
        String ubicacion,
        Integer bateria,
        Integer senal,
        Long ultimoUpdate,
        String ultimoUpdateLabel,
        String estado,
        String estadoLabel,
        String estadoSoft,
        String estadoInk,
        boolean bateriaBaja,
        String falla,
        Long fallaDesde
) {}
