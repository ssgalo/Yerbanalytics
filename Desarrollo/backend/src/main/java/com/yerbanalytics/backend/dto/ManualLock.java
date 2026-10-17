package com.yerbanalytics.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bloqueo manual vigente tal como lo ve el dashboard. Las claves del JSON son el contrato de la API
 * ({@code bloqueo-manual}), por eso conservan su nombre en español.
 *
 * @param zonaId      macro-zona afectada; en un bloqueo de sector es la zona a la que pertenece
 * @param sectorId    sector bloqueado; {@code null} si el bloqueo es de toda la macro-zona
 * @param activatedBy usuario que lo activó
 * @param activatedAt instante de activación (epoch ms)
 * @param reason      motivo declarado
 */
public record ManualLock(
        Long id,
        String zonaId,
        String sectorId,
        @JsonProperty("activadoPor") String activatedBy,
        @JsonProperty("activadoTs") long activatedAt,
        @JsonProperty("motivo") String reason
) {}
