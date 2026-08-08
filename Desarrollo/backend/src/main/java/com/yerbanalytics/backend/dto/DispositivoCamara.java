package com.yerbanalytics.backend.dto;

/** Estado tecnico de un dispositivo de captura. */
public record DispositivoCamara(
        String id,
        String nombre,
        String plataforma,
        String estado,
        String estadoLabel,
        String estadoSoft,
        String estadoInk,
        Long ultimoHeartbeat,
        String ultimoHeartbeatAgo,
        boolean capturaListo,
        int capturasOk,
        int capturasError
) {}
