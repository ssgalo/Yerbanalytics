package com.yerbanalytics.backend.dto;

/**
 * Cuerpo de {@code PUT /api/rules/{name}/estado}.
 *
 * @param activa             {@code true} prende, {@code false} apaga (obligatorio)
 * @param motivo             por qué; obligatorio para apagar una protección, opcional si no, hasta 500 caracteres
 * @param confirmaProteccion {@code true} confirma que se leyó la consecuencia de apagar una protección
 */
public record CambioEstadoReglaRequest(Boolean activa, String motivo, Boolean confirmaProteccion) {}
