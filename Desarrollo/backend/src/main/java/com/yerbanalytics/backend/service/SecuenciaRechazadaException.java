package com.yerbanalytics.backend.service;

/** No se puede iniciar o cancelar la secuencia ahora (409); el mensaje es legible y viaja tal cual. */
public class SecuenciaRechazadaException extends RuntimeException {
    public SecuenciaRechazadaException(String mensaje) {
        super(mensaje);
    }
}
