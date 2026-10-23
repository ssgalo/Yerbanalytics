package com.yerbanalytics.backend.mediasombra;

import java.time.LocalTime;

/**
 * Ventana diaria en que la mediasombra de una macro-zona está abierta (hora local del vivero): abierta de {@code desde}
 * a {@code hasta}, cerrada el resto del día. No cruza la medianoche: la malla se abre de día.
 */
public record VentanaDiaria(LocalTime desde, LocalTime hasta) {

    /** {@code true} si a esa hora la malla debería estar abierta ({@code desde} incluido, {@code hasta} excluido). */
    public boolean abiertaA(LocalTime hora) {
        return !hora.isBefore(desde) && hora.isBefore(hasta);
    }

    /** "08:00–17:00". */
    public String texto() {
        return desde + "–" + hasta;
    }
}
