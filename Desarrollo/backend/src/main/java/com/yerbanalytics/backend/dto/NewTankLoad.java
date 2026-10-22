package com.yerbanalytics.backend.dto;

/**
 * Carga de un tanque. Quién la hace sale de la sesión, no del cuerpo.
 *
 * @param liters         litros que se agregan (obligatorio, mayor que cero)
 * @param capacityLiters capacidad del tanque; obligatoria la primera vez (el tanque todavía no existe) y opcional
 *                       después, para corregirla
 */
public record NewTankLoad(Double liters, Double capacityLiters) {
}
