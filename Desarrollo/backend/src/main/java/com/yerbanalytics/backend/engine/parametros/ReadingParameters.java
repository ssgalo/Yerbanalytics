package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Parámetros del historial de lecturas. No son umbrales de una regla sino de infraestructura que las reglas
 * suponen: cuánto pasado hay disponible para consultar.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ReadingParameters implements DefinicionParametro {

    RETENTION_DAYS("readings.retention-days",
            "Retención del historial de lecturas",
            "Días que se conservan las lecturas de cada macro-zona. Las reglas miran hasta 72 h hacia atrás; el resto es margen para auditar. Lo más viejo se borra todos los días a las 03:30.",
            TipoParametro.ENTERO, "días", "14", 3.0, 90.0, 0, "add-historial-lecturas D-4");

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
        return FamiliaParametro.READINGS;
    }
}
