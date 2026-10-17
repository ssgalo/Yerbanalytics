package com.yerbanalytics.backend.engine;

/**
 * Dosis de insumo que decidió la regla: lo que el {@link ActionExecutor} manda al nodo en {@code pump ON {"ml"}}.
 * La cantidad la decide el backend (parámetro {@code insumo.dosis-ml} del catálogo), no el valor por defecto del firmware.
 *
 * @param ml mililitros a dosificar (1–50, el tope que acepta el firmware)
 */
public record DetalleInsumo(int ml) implements DetalleAccion {
}
