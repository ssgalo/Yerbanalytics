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
            "Con un índice UV de pronóstico igual o mayor a este, la mediasombra pasa a posición protectora.",
            TipoParametro.NUMERO, "índice", "7", 1.0, 11.0, 1, "motor-reglas: ShadingRule"),
    APERTURA_PROTECCION_UV("mediasombra.apertura-proteccion-uv",
            "Apertura protectora ante pico UV",
            "Apertura de la mediasombra cuando se supera el índice UV de protección.",
            TipoParametro.ENTERO, "%", "30", 0.0, 100.0, 0, "motor-reglas: ShadingRule"),
    APERTURA_MAXIMA("mediasombra.apertura-maxima",
            "Apertura máxima de la mediasombra",
            "Tope de apertura que admite el plan de rustificación.",
            TipoParametro.ENTERO, "%", "100", 10.0, 100.0, 0, "motor-reglas: ShadingRule");

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
