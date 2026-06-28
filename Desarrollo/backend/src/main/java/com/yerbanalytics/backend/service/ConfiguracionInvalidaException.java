package com.yerbanalytics.backend.service;

/**
 * Se lanza cuando una configuración propuesta viola un límite fisiológico o de
 * coherencia (HU-15 CA-03). El controller la traduce a un 400 con su mensaje.
 */
public class ConfiguracionInvalidaException extends RuntimeException {
    public ConfiguracionInvalidaException(String message) {
        super(message);
    }
}
