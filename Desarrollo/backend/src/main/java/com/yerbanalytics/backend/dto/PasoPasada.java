package com.yerbanalytics.backend.dto;

/**
 * Un paso de la pasada del riel (design §2.6). Todas las claves se serializan siempre, con
 * {@code null} cuando no aplican. {@code tipo}: MOVER | CAPTURAR | HOME. {@code estado}:
 * PENDIENTE | EN_CURSO | OK | ERROR | OMITIDO. Timestamps en ms epoch.
 */
public record PasoPasada(
        int n,
        String tipo,
        int posicion,
        String sectorId,
        String estado,
        String codigoError,
        String detalle,
        String commandId,
        String ordenId,
        String estadoOrden,
        String capturaId,
        String imagenUrl,
        DiagnosticoPaso diagnostico,
        Long iniciadoEn,
        Long terminadoEn
) {
    public PasoPasada conDiagnostico(DiagnosticoPaso d) {
        return new PasoPasada(n, tipo, posicion, sectorId, estado, codigoError, detalle, commandId,
                ordenId, estadoOrden, capturaId, imagenUrl, d, iniciadoEn, terminadoEn);
    }
}
