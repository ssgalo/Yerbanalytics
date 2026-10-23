package com.yerbanalytics.backend.dto;

/**
 * El plan de nutrición de una macro-zona (reglas_v2 §8.4), tal como lo muestra su detalle.
 *
 * @param intervalDays        días entre dos aplicaciones del plan (el de la zona, o el del catálogo si no fijó uno)
 * @param customInterval      {@code true} si la zona fijó el suyo
 * @param lastApplicationTs   epoch ms en que terminó la última tanda del plan; {@code null} si nunca hubo una
 * @param nextDueTs           epoch ms desde el que el plan está vencido; {@code null} si ya lo está (nunca se aplicó) o hay
 *                            una tanda en curso
 * @param batchOpen           hay una tanda en curso: el plan ya pidió el fertilizante y algún sector espera su ventana
 * @param advanced            N-02 adelantó el plan por CE baja y todavía no se ejecutó
 */
public record NutritionPlan(
        int intervalDays,
        boolean customInterval,
        Long lastApplicationTs,
        Long nextDueTs,
        boolean batchOpen,
        boolean advanced
) {}
