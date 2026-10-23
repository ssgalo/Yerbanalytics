package com.yerbanalytics.backend.engine;

/**
 * Posición que pide una {@link ActionType#MOVER_MEDIASOMBRA}. La malla sólo abre o cierra del todo (el firmware enrolla
 * con una apertura &ge; 50 y despliega por debajo), así que la posición es binaria.
 *
 * @param abrir {@code true} enrollar (abierta, sol directo); {@code false} desplegar (cerrada, sombra)
 */
public record DetalleMediasombra(boolean abrir) implements DetalleAccion {
}
