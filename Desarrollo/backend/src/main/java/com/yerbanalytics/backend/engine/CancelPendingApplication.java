package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.supply.Supply;

/**
 * Detalle de un {@code ABORT_INSUMO} que, además de cortar la rama de insumos del ciclo, retira la aplicación que otra
 * regla dejó pendiente para el sector: apareció una razón por la que ya no corresponde (F-C1: una causa de la clorosis que el
 * fertilizante no corrige) y esperar la próxima ventana la aplicaría igual.
 *
 * <p>Es el equivalente en insumos de {@link CancelaRiego}. Sólo retira el pedido de {@code requestedBy}: el de otra regla
 * (por ejemplo el plan de nutrición) no es de quien corta.
 *
 * @param supply      el insumo cuya aplicación pendiente se retira
 * @param requestedBy la regla que la pidió
 */
public record CancelPendingApplication(Supply supply, String requestedBy) implements DetalleAccion {
}
