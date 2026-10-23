package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.supply.Supply;

/**
 * A qué sector le toca el tratamiento que decidió una regla de macro-zona (el foco, F-04). La regla de zona decide sobre
 * varios sectores de una vez, así que cada orden lleva el suyo; la cantidad no viaja acá: la fija el
 * {@code ApplicationService} con la dosis del catálogo.
 *
 * <p>No exige diagnóstico: los lindantes de un foco están sanos o sin diagnosticar (P-D1 no rige para ellos).
 *
 * @param sectorId el sector al que se le pide la aplicación
 * @param supply   el insumo a aplicar
 */
public record DetalleOutbreakTreatment(String sectorId, Supply supply) implements DetalleAccion {
}
