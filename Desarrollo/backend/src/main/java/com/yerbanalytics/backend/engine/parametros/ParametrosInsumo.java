package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Parámetros de la rama de insumos. */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosInsumo implements DefinicionParametro {

    MAX_DOSIS_24H("insumo.max-dosis-24h",
            "Máximo de dosis en 24 h",
            "Con esta cantidad de dosificaciones en 24 h el sector alcanzó su límite y no se le aplican más insumos.",
            TipoParametro.ENTERO, "dosis", "1", 1.0, 10.0, 0, "motor-reglas: DailyDoseLimitRule"),

    DOSIS_ML("insumo.dosis-ml",
            "Dosis de insumo por aplicación",
            "Mililitros que dosifica la bomba peristáltica en cada aplicación. El backend se lo manda al nodo en el comando; el firmware no acepta más de 50 ml por comando.",
            TipoParametro.ENTERO, "ml", "10", 1.0, 50.0, 0, "add-dosificacion-base (parcial); firmware LIMITE_BOMBA_ML_MAX");

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
        return FamiliaParametro.INSUMO;
    }
}
