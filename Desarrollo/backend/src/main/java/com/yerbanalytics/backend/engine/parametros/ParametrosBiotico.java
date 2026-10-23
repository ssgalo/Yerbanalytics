package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Parámetros de las reglas bióticas: hongo (F-F1, F-F2), plagas (F-P1) y foco (F-04) (reglas_v2 §8.5).
 *
 * <p>Los rangos de las temperaturas predisponentes y de las horas de las ventanas los propone el equipo: la v2 fija el
 * valor de fábrica pero no cuánto se puede mover. Las dos temperaturas tienen rangos que no se tocan, así nunca quedan
 * invertidas.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosBiotico implements DefinicionParametro {

    DPV_SATURATED("biotico.dpv-saturado",
            "DPV de aire saturado",
            "Con un déficit de presión de vapor menor a este el aire está casi saturado y favorece al hongo (F-F1, F-F2).",
            TipoParametro.NUMERO, "kPa", "0.3", 0.1, 0.5, 2, "reglas_v2 §8.5 F-F1 y F-F2"),
    DPV_DRY_HEAT("biotico.dpv-calor-seco",
            "DPV de calor seco",
            "Con un déficit de presión de vapor igual o mayor a este (calor y aire seco) los ácaros se favorecen (F-P1).",
            TipoParametro.NUMERO, "kPa", "2", 1.5, 3.0, 1, "reglas_v2 §8.5 F-P1"),
    FUNGAL_MOISTURE("biotico.humedad-hongo",
            "Humedad de sustrato que favorece al hongo",
            "Con la humedad de sustrato por encima de esta en dos lecturas seguidas, el hongo se favorece (F-F1).",
            TipoParametro.NUMERO, "%", "80", 70.0, 90.0, 0, "reglas_v2 §8.5 F-F1"),
    PREDISPOSING_TEMP_MIN("biotico.temp-predisponente-min",
            "Temperatura mínima predisponente al hongo",
            "Debajo de esta temperatura del aire no se avisa de condiciones favorables al hongo (F-F2). Rango propuesto por el equipo.",
            TipoParametro.NUMERO, "°C", "18", 10.0, 22.0, 0, "reglas_v2 §8.5 F-F2"),
    PREDISPOSING_TEMP_MAX("biotico.temp-predisponente-max",
            "Temperatura máxima predisponente al hongo",
            "Encima de esta temperatura del aire no se avisa de condiciones favorables al hongo (F-F2). Rango propuesto por el equipo.",
            TipoParametro.NUMERO, "°C", "28", 24.0, 35.0, 0, "reglas_v2 §8.5 F-F2"),
    PREDISPOSING_MOISTURE("biotico.humedad-predisponente",
            "Humedad de sustrato predisponente al hongo",
            "Con la humedad de sustrato igual o mayor a esta (y aire saturado y temperatura templada) se avisa de condiciones favorables al hongo (F-F2).",
            TipoParametro.NUMERO, "%", "75", 65.0, 85.0, 0, "reglas_v2 §8.5 F-F2"),
    CONSECUTIVE_CAPTURES("biotico.capturas-seguidas",
            "Capturas seguidas que confirman el diagnóstico",
            "Cuántas capturas diarias seguidas con el mismo diagnóstico alcanzan para tratar aunque sea leve y el ambiente no lo favorezca (F-F1, F-P1).",
            TipoParametro.ENTERO, "capturas", "3", 2.0, 5.0, 0, "reglas_v2 §8.5 F-F1 y F-P1"),
    MITE_DPV_HOURS("biotico.horas-dpv-acaros",
            "Horas del DPV que favorece a los ácaros",
            "Cuántas horas hacia atrás se busca un déficit de presión de vapor de calor seco (F-P1).",
            TipoParametro.ENTERO, "h", "72", 24.0, 120.0, 0, "reglas_v2 §8.5 F-P1"),
    OUTBREAK_SECTORS("biotico.sectores-foco",
            "Sectores que forman un foco",
            "Cuántos sectores de la macro-zona con el mismo diagnóstico biótico vigente forman un foco (F-04).",
            TipoParametro.ENTERO, "sectores", "3", 2.0, 10.0, 0, "reglas_v2 §8.5 F-04"),
    OUTBREAK_HOURS("biotico.horas-foco",
            "Ventana del foco",
            "Dentro de cuántas horas tienen que haber llegado los diagnósticos de los sectores para formar un foco (F-04).",
            TipoParametro.ENTERO, "h", "72", 24.0, 120.0, 0, "reglas_v2 §8.5 F-04");

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
        return FamiliaParametro.BIOTICO;
    }
}
