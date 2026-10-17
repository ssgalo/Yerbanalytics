package com.yerbanalytics.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pedido de bloqueo manual. Quién lo activa sale de la sesión, no del cuerpo.
 *
 * @param zonaId   macro-zona (obligatoria)
 * @param sectorId sector a bloquear; {@code null} bloquea toda la macro-zona
 * @param reason   motivo (obligatorio)
 */
public record NewManualLock(
        String zonaId,
        String sectorId,
        @JsonProperty("motivo") String reason
) {}
