package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Parámetros de la rama de mediasombra. */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosMediasombra implements DefinicionParametro {

    UV_UMBRAL("mediasombra.uv-umbral",
            "Índice UV de protección",
            "Con un índice UV de pronóstico igual o mayor a este, la mediasombra se cierra (el hardware es binario: abierta o cerrada).",
            TipoParametro.NUMERO, "índice", "7", 1.0, 11.0, 1, "motor-reglas: ShadingRule");

    private final String clave;
    private final String etiqueta;
    private final String descripcion;
    private final TipoParametro tipo;
    private final String unidad;
    private final String fabrica;
    private final Double min;
    private final Double max;
    private final int decimales;
    private final String refSpec;

    @Override
    public FamiliaParametro familia() {
        return FamiliaParametro.MEDIASOMBRA;
    }
}
