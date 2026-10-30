package com.yerbanalytics.backend.dto;

/**
 * Cuerpo de {@code POST /api/secuencias}: {@code {"tipo":"RIEGO","parametros":{"duracionSeg":15},"sectorId":"MZ-1-002"}}.
 * {@code tipo}: RIEGO | DOSIS | MEDIASOMBRA | LECTURA | MEDIASOMBRA_ABRIR | MEDIASOMBRA_CERRAR. {@code parametros} es opcional.
 * {@code sectorId} también: sólo vale para RIEGO y DOSIS y tiene que ser un sector de la zona de destino; sin él se usa el
 * primero de la zona.
 */
public record IniciarSecuencia(String tipo, ParametrosSecuencia parametros, String sectorId) {

    /** Sin sector elegido: el primero de la zona. */
    public IniciarSecuencia(String tipo, ParametrosSecuencia parametros) {
        this(tipo, parametros, null);
    }
}
