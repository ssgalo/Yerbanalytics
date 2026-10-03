package com.yerbanalytics.backend.engine;

/**
 * Datos tipados que viajan con una {@link RuleAction} en lugar de ir pegados al texto del motivo
 * (antes la duración del riego se extraía del motivo con una regex). El motivo sigue siendo el
 * texto legible para el usuario; el detalle es lo que el sistema ejecuta.
 */
public sealed interface DetalleAccion permits DetalleRiego, DetalleAlerta {
}
