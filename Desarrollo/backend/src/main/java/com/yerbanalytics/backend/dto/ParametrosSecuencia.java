package com.yerbanalytics.backend.dto;

/**
 * Parámetros de una secuencia, de entrada y de salida. {@code duracionSeg} es del riego (segundos con
 * la válvula abierta), {@code esperaSeg} de la mediasombra (segundos desplegada), {@code ejecutarMotor} de la
 * lectura (además de mostrar los valores, evalúa el motor de reglas de la zona) y {@code ml} de la dosis
 * (mililitros de la bomba de insumo); el que no aplica viaja {@code null}.
 */
public record ParametrosSecuencia(Integer duracionSeg, Integer esperaSeg, Boolean ejecutarMotor, Integer ml) {

    /** Riego o mediasombra: sin {@code ejecutarMotor} ni {@code ml}. */
    public ParametrosSecuencia(Integer duracionSeg, Integer esperaSeg) {
        this(duracionSeg, esperaSeg, null, null);
    }

    /** Lectura: sin {@code ml}. */
    public ParametrosSecuencia(Integer duracionSeg, Integer esperaSeg, Boolean ejecutarMotor) {
        this(duracionSeg, esperaSeg, ejecutarMotor, null);
    }
}
