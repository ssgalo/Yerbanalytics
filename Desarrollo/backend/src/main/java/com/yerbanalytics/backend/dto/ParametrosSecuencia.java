package com.yerbanalytics.backend.dto;

/**
 * Parámetros de una secuencia, de entrada y de salida. {@code duracionSeg} es del riego (segundos con
 * la válvula abierta), {@code esperaSeg} de la mediasombra (segundos desplegada) y {@code ejecutarMotor} de la
 * lectura (además de mostrar los valores, evalúa el motor de reglas de la zona); el que no aplica viaja
 * {@code null}.
 */
public record ParametrosSecuencia(Integer duracionSeg, Integer esperaSeg, Boolean ejecutarMotor) {

    /** Riego o mediasombra: sin {@code ejecutarMotor}. */
    public ParametrosSecuencia(Integer duracionSeg, Integer esperaSeg) {
        this(duracionSeg, esperaSeg, null);
    }
}
