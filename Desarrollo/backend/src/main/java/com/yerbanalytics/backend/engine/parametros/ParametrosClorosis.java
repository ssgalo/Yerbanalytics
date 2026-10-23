package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Parámetros de F-C1, la clorosis con causa no nutricional (reglas_v2 §8.5): lo que descarta el fertilizante correctivo.
 * Los rangos los propone el equipo: la v2 fija el valor de fábrica pero no cuánto se puede mover.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosClorosis implements DefinicionParametro {

    WATERLOGGING_MOISTURE("clorosis.humedad-asfixia",
            "Humedad de sustrato que asfixia la raíz",
            "Con la humedad de sustrato media por encima de esta, la clorosis se atribuye a asfixia radicular y no se fertiliza.",
            TipoParametro.NUMERO, "%", "80", 70.0, 90.0, 0, "reglas_v2 §8.5 F-C1"),
    WATERLOGGING_HOURS("clorosis.humedad-horas",
            "Horas de la humedad media",
            "Cuántas horas hacia atrás se promedia la humedad de sustrato para detectar la asfixia radicular.",
            TipoParametro.ENTERO, "h", "48", 24.0, 72.0, 0, "reglas_v2 §8.5 F-C1"),
    SALINITY_EC("clorosis.ce-salinidad",
            "CE que indica salinidad",
            "Con la conductividad del sustrato igual o mayor a esta, la clorosis se atribuye a salinidad y no se fertiliza.",
            TipoParametro.NUMERO, "dS/m", "2", 1.5, 3.0, 1, "reglas_v2 §8.5 F-C1"),
    IRON_CHLOROSIS_PH("clorosis.ph-ferrica",
            "pH que indica clorosis férrica",
            "Con el pH del sustrato igual o mayor a este, la clorosis se atribuye a pH alto (clorosis férrica) y no se fertiliza.",
            TipoParametro.NUMERO, "", "6.5", 6.0, 7.0, 1, "reglas_v2 §8.5 F-C1");

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
        return FamiliaParametro.CLOROSIS;
    }
}
