package com.yerbanalytics.backend.dto;

/**
 * La revisión registrada de un sector que había alcanzado el tope de fitosanitarios (F-06).
 *
 * @param sectorId   el sector revisado
 * @param reviewedTs cuándo se registró (epoch ms, reloj del vivero): desde ahí el conteo del tope vuelve a cero
 */
public record FungicideReview(String sectorId, long reviewedTs) {
}
