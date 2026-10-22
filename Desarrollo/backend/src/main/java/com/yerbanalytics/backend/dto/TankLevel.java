package com.yerbanalytics.backend.dto;

/**
 * Tanque de un insumo en una macro-zona tal como lo ve el dashboard.
 *
 * @param zoneId     macro-zona del tanque
 * @param supply     insumo ({@code FERTILIZER} | {@code FUNGICIDE})
 * @param supplyLabel nombre para mostrar ("Fertilizante", "Fitosanitario")
 * @param capacityMl capacidad en mililitros
 * @param levelMl    nivel actual en mililitros
 * @param levelPct   nivel como porcentaje de la capacidad
 * @param low        {@code true} si está por debajo del aviso de reposición ({@code insumo.stock-aviso})
 * @param updatedTs  último cambio de nivel (epoch ms)
 * @param updatedBy  quién hizo la última carga; {@code null} si desde entonces sólo hubo aplicaciones
 */
public record TankLevel(
        String zoneId,
        String supply,
        String supplyLabel,
        long capacityMl,
        long levelMl,
        double levelPct,
        boolean low,
        long updatedTs,
        String updatedBy) {
}
