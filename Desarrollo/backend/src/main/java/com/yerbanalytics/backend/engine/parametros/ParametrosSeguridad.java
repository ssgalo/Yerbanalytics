package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Parámetros de seguridad del motor: condiciones que frenan todo antes de actuar. */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosSeguridad implements DefinicionParametro {

    ANTIGUEDAD_MAX_LECTURA("seguridad.antiguedad-max-lectura",
            "Antigüedad máxima de la lectura",
            "Si la última lectura de la zona es más vieja que esto, se considera que el sensor dejó de reportar y se bloquea la evaluación. La fábrica son 3 intervalos de publicación del nodo (30 s).",
            TipoParametro.ENTERO, "s", "90", 10.0, 600.0, 0, "motor-reglas: StaleSensorRule");

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
        return FamiliaParametro.SEGURIDAD;
    }
}
