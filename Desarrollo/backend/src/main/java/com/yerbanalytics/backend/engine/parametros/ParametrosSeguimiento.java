package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Seguimiento post-acción (HU-12): cuánto se espera antes de verificar si lo que hizo el motor surtió efecto y cuánto
 * tiene que recuperarse la métrica para considerarlo efectivo. Vivían en la configuración operativa; el catálogo es la
 * única fuente de umbrales que deciden, y los lee {@code HistorialService}, que los declara.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosSeguimiento implements DefinicionParametro {

    LATENCY_MIN("seguimiento.latencia-min",
            "Espera antes de evaluar el efecto",
            "Minutos que se espera, después de una acción del motor, antes de comparar la métrica y decidir si fue efectiva.",
            TipoParametro.ENTERO, "min", "2", 1.0, 1440.0, 0, "HU-12 CA-02"),

    DELTA_MIN("seguimiento.delta-min",
            "Recuperación mínima para considerar efectiva",
            "Puntos que tiene que mejorar la métrica de la acción dentro de la espera para catalogarla como efectiva.",
            TipoParametro.NUMERO, "puntos", "5", 0.5, 50.0, 1, "HU-12 CA-02");

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
        return FamiliaParametro.SEGUIMIENTO;
    }
}
