package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Parámetros del plan de nutrición y de la regla de CE baja (reglas_v2 §8.4).
 *
 * <p>{@link #PLAN_INTERVAL} es el valor inicial de cada macro-zona: la zona puede fijar el suyo
 * ({@code zona.nutrition_plan_interval_days}) y mientras no lo haga rige éste. Su rango (3–30 días) es también el
 * que valida el intervalo propio de la zona.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosNutricion implements DefinicionParametro {

    PLAN_INTERVAL("nutricion.intervalo-plan",
            "Intervalo del plan de nutrición",
            "Días entre dos aplicaciones del plan de nutrición en una macro-zona. Es el valor de cada zona mientras no fije el suyo en su detalle.",
            TipoParametro.NUMERO, "d", "7", 3.0, 30.0, 0, "reglas_v2 §8.4 N-01"),
    LOW_EC("nutricion.ce-baja",
            "CE baja",
            "Con una CE media menor a esta (y sin fertilizar hace poco) se adelanta el plan de nutrición.",
            TipoParametro.NUMERO, "dS/m", "0.5", 0.3, 1.0, 2, "reglas_v2 §8.4 N-02"),
    EC_SAMPLES("nutricion.ce-lecturas",
            "Lecturas de CE para la media",
            "Cuántas de las últimas lecturas de pulso entran en la media de CE. Con menos lecturas que éstas la regla no decide.",
            TipoParametro.ENTERO, "lecturas", "6", 3.0, 12.0, 0, "reglas_v2 §8.4 N-02"),
    NO_FERTILIZED_DAYS("nutricion.sin-fertilizar-dias",
            "Días sin fertilizar para adelantar el plan",
            "La CE baja sólo adelanta el plan si en la macro-zona no hubo ninguna fertilización (del plan o correctiva) en estos días.",
            TipoParametro.NUMERO, "d", "5", 3.0, 14.0, 0, "reglas_v2 §8.4 N-02");

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
        return FamiliaParametro.NUTRICION;
    }
}
