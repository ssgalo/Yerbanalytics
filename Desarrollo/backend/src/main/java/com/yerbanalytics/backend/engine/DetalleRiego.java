package com.yerbanalytics.backend.engine;

/**
 * Orden de riego calculada: lo que la regla decidió antes de abrir la válvula.
 *
 * @param volumenL    volumen a aplicar, en litros (redondeado a 0,01 L)
 * @param duracionSeg tiempo de apertura, en segundos ({@code ceil(V / caudal × 3600)}, acotado al contrato)
 * @param humedad     humedad de sustrato (%) que motivó el riego
 * @param recortado   {@code true} si la duración se recortó al máximo del contrato de la válvula
 */
public record DetalleRiego(double volumenL, int duracionSeg, double humedad, boolean recortado)
        implements DetalleAccion {
}
