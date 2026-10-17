package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.engine.parametros.VentanaHoraria;

import java.time.Instant;
import java.util.Objects;

/**
 * Qué parte del historial mira una consulta: un rango de tiempo, una franja horaria local opcional (p. ej. 10–16 h
 * para la luz) y si cuentan sólo las lecturas de pulso. Inmutable: cada ajuste devuelve una ventana nueva.
 *
 * @param from         inicio del rango, inclusive
 * @param to           fin del rango, inclusive; {@code null} = hasta la última lectura
 * @param band         franja horaria local del vivero, o {@code null} para todo el día. Se evalúa con
 *                     {@link VentanaHoraria#contieneHastaElMinuto}: la lectura de la hora de fin todavía entra, igual
 *                     que en la ventana de riego
 * @param requirePulse {@code true} = sólo {@link ReadingOrigin#PULSE}. Para "N lecturas seguidas" o "media de N
 *                     lecturas"; los máximos y las medias de una ventana temporal (48 h, 72 h) cuentan todas
 */
public record ReadingWindow(Instant from, Instant to, VentanaHoraria band, boolean requirePulse) {

    public ReadingWindow {
        Objects.requireNonNull(from, "from");
        if (to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("El fin de la ventana es anterior a su inicio.");
        }
    }

    /** Desde {@code from} hasta la última lectura, todo el día, todos los orígenes. */
    public static ReadingWindow since(Instant from) {
        return new ReadingWindow(from, null, null, false);
    }

    public static ReadingWindow between(Instant from, Instant to) {
        return new ReadingWindow(from, to, null, false);
    }

    public ReadingWindow withBand(VentanaHoraria band) {
        return new ReadingWindow(from, to, band, requirePulse);
    }

    /** Sólo cuentan las lecturas que respondieron a un pulso. */
    public ReadingWindow onlyPulse() {
        return new ReadingWindow(from, to, band, true);
    }
}
