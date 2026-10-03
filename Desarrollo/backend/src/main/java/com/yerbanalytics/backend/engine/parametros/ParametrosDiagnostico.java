package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Parámetros que dependen del diagnóstico de IA. */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosDiagnostico implements DefinicionParametro {

    CONFIANZA_MINIMA("diagnostico.confianza-minima",
            "Confianza mínima del diagnóstico",
            "Un diagnóstico de IA con confianza menor a esta no es concluyente y no dispara dosificación.",
            TipoParametro.NUMERO, "%", "85", 50.0, 100.0, 0, "motor-reglas: SupplyRule; HU-04 CA-03");

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
        return FamiliaParametro.DIAGNOSTICO;
    }
}
