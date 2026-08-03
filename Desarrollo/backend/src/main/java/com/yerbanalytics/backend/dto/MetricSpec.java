package com.yerbanalytics.backend.dto;

/**
 * Especificación de una métrica sensada: metadatos de presentación y bandas
 * ideal/warn/crit.
 *
 * @param base         valor central de referencia. Es {@code Double} —y no entero— porque
 *                     métricas como {@code ce} (1,4 dS/m) o {@code phSuelo} (5,5) tienen su
 *                     centro fuera de los enteros.
 * @param grupo        agrupación visual del panel de sensado: {@code ambiente} | {@code nutricion}
 * @param afectaEstado si la métrica participa del cálculo del estado de salud de los
 *                     sectores. Las que llegaron con la sonda de suelo (tempSuelo, phSuelo,
 *                     n, p, k) son informativas hasta validar sus rangos con el vivero: se
 *                     muestran y colorean, pero no cambian el estado. Ver
 *                     {@code openspec/changes/move-sensado-macrozona}.
 */
public record MetricSpec(
        String key, String label, String unit, Double[] ideal,
        Double[] warn, Double[] crit, Integer dec, Double base,
        String grupo, Boolean afectaEstado
) {}
