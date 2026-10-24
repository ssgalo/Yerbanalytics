package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Clave de una copia de parámetro: la pieza dueña ({@link ConsumidorParametros#name()}) y la clave del catálogo. Es una
 * clase (no un record) porque JPA la instancia vacía y le asigna los campos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReglaParametroId implements Serializable {

    /** Nombre técnico de la pieza (p. ej. {@code RiegoPorDeficitRule}, {@code DespachoRiego}). */
    private String consumidor;

    /** Clave del catálogo (p. ej. {@code riego.umbral-humedad}). */
    private String clave;
}
