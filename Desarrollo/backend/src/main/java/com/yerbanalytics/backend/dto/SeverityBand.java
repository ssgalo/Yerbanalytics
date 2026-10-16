package com.yerbanalytics.backend.dto;

/**
 * Bandas de severidad de un estado anómalo, en cantidad de hojas por sector: bajo
 * {@code minimum} el estado no cuenta; desde {@code minimum}, severidad Baja; desde
 * {@code mediumFrom}, Media; desde {@code highFrom}, Alta.
 *
 * <p>Los números se declaran {@link Number} para poder rechazar un valor no entero con un
 * mensaje claro: con {@code Integer}, Jackson truncaría {@code 2.5} a {@code 2} sin avisar.
 */
public record SeverityBand(String state, Number minimum, Number mediumFrom, Number highFrom) {
}
