package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Parámetros de la evaluación de efectividad (E-01…E-03, reglas_v2 §4 y §9): cuánto tiene que subir la humedad para que un riego
 * cuente como efectivo y cuánto espera el sistema antes de juzgar cada tipo de acción. Los rangos los propone el equipo: la v2 fija
 * el valor de fábrica pero no cuánto se puede mover.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosEfectividad implements DefinicionParametro {

    IRRIGATION_DELTA("efectividad.delta-riego",
            "Subida de humedad de un riego efectivo",
            "Cuántos puntos tiene que subir la humedad de sustrato de la macro-zona entre la lectura previa y la de verificación para que el riego cuente como efectivo.",
            TipoParametro.NUMERO, "%", "8", 3.0, 20.0, 0, "reglas_v2 E-01"),
    VERIFICATION_MINUTES("efectividad.verificacion-min",
            "Espera de la lectura de verificación",
            "Cuántos minutos después de cerrarse el riego se pide la lectura de verificación a la macro-zona.",
            TipoParametro.ENTERO, "min", "30", 15.0, 60.0, 0, "reglas_v2 E-01"),
    FERTILIZER_LATENCY_DAYS("efectividad.latencia-fertilizante-dias",
            "Espera para juzgar el fertilizante correctivo",
            "Cuántos días después de la primera aplicación correctiva se evalúa si el diagnóstico del sector mejoró.",
            TipoParametro.ENTERO, "d", "14", 7.0, 30.0, 0, "reglas_v2 E-02"),
    FUNGICIDE_LATENCY_DAYS("efectividad.latencia-fitosanitario-dias",
            "Espera para juzgar el fitosanitario",
            "Cuántos días después del fitosanitario se evalúa si el daño dejó de avanzar.",
            TipoParametro.ENTERO, "d", "7", 3.0, 21.0, 0, "reglas_v2 E-03"),
    NO_DIAGNOSIS_GRACE_HOURS("efectividad.espera-sin-diagnostico-horas",
            "Gracia sin diagnóstico vigente",
            "Si al vencer la espera el sector no tiene un diagnóstico vigente, cuántas horas más se aguarda uno antes de dar la acción por sin efectividad por falta de dato.",
            TipoParametro.ENTERO, "h", "48", 12.0, 96.0, 0, "reglas_v2 E-02/E-03");

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
        return FamiliaParametro.EFECTIVIDAD;
    }
}
