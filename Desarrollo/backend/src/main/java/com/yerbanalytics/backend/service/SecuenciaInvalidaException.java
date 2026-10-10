package com.yerbanalytics.backend.service;

/** El pedido de secuencia es inválido (400): tipo desconocido o parámetro fuera de rango. */
public class SecuenciaInvalidaException extends RuntimeException {
    public SecuenciaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
