package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosInsumo;

/**
 * Los insumos que el vivero aplica (reglas_v2 §8.1). Cada uno tiene su dosis y su intervalo mínimo en el catálogo de
 * parámetros, y su tanque en cada macro-zona.
 *
 * <p>El nombre que se persiste y viaja por la API es el de la constante; {@link #label()} es lo que ve el operador.
 */
public enum Supply {

    FERTILIZER("Fertilizante", ParametrosInsumo.FERTILIZER_DOSE, ParametrosInsumo.FERTILIZER_INTERVAL,
            "interval-fertilizer"),
    FUNGICIDE("Fitosanitario", ParametrosInsumo.FUNGICIDE_DOSE, ParametrosInsumo.FUNGICIDE_INTERVAL,
            "interval-fungicide");

    private final String label;
    private final DefinicionParametro dose;
    private final DefinicionParametro interval;
    private final String intervalCooldownKey;

    Supply(String label, DefinicionParametro dose, DefinicionParametro interval, String intervalCooldownKey) {
        this.label = label;
        this.dose = dose;
        this.interval = interval;
        this.intervalCooldownKey = intervalCooldownKey;
    }

    public String label() {
        return label;
    }

    /** Parámetro del catálogo con los mililitros de una aplicación. */
    public DefinicionParametro doseParameter() {
        return dose;
    }

    /** Parámetro del catálogo con los días mínimos entre dos aplicaciones en un sector. */
    public DefinicionParametro intervalParameter() {
        return interval;
    }

    /** Clave del cooldown del simulador que acorta el intervalo (minutos; 0 lo elimina). */
    public String intervalCooldownKey() {
        return intervalCooldownKey;
    }

    /** El fitosanitario va antes que el fertilizante cuando corresponden ambos el mismo día (P-D2). */
    public int order() {
        return this == FUNGICIDE ? 0 : 1;
    }
}
