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
            TipoParametro.NUMERO, "%", "70", 50.0, 95.0, 0, "reglas_v2 §1.3 y §11; HU-04 CA-03"),
    VALIDITY_HOURS("diagnostico.vigencia-horas",
            "Vigencia del diagnóstico",
            "Horas que un diagnóstico de IA sigue valiendo para aplicar un insumo. Pasado ese tiempo hay que esperar uno nuevo.",
            TipoParametro.ENTERO, "h", "48", 12.0, 168.0, 0, "reglas_v2 §1.3 P-D1");

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
