package com.yerbanalytics.backend.engine;

/**
 * Alerta que acompaña a una acción {@link ActionType#ALERTA}. Su alcance es la macro-zona:
 * se persiste una sola vez por macro-zona, regla y ciclo de lectura.
 */
public record DetalleAlerta(NivelAlerta nivel, String texto) implements DetalleAccion {
}
