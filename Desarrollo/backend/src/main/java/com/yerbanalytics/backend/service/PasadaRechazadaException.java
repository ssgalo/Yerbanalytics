package com.yerbanalytics.backend.service;

/** No se puede iniciar o cancelar la pasada; el mensaje es legible y viaja tal cual en el 409. */
public class PasadaRechazadaException extends RuntimeException {
    public PasadaRechazadaException(String mensaje) {
        super(mensaje);
    }
}
