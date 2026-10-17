package com.yerbanalytics.backend.lecturas;

import com.yerbanalytics.backend.engine.parametros.VentanaHoraria;

import java.time.Instant;
import java.util.Objects;

/**
 * Qué parte del historial mira una consulta: un rango de tiempo, una franja horaria local opcional (p. ej. 10–16 h
 * para la luz) y si cuentan sólo las lecturas de pulso. Inmutable: cada ajuste devuelve una ventana nueva.
 *
 * @param desde     inicio del rango, inclusive
 * @param hasta     fin del rango, inclusive; {@code null} = hasta la última lectura
 * @param franja    franja horaria local del vivero, o {@code null} para todo el día. Se evalúa con
 *                  {@link VentanaHoraria#contieneHastaElMinuto}: la lectura de la hora de fin todavía entra, igual que
 *                  en la ventana de riego
 * @param exigePulso {@code true} = sólo {@link OrigenLectura#PULSO}. Para "N lecturas seguidas" o "media de N lecturas";
 *                  los máximos y las medias de una ventana temporal (48 h, 72 h) cuentan todas
 */
public record VentanaLecturas(Instant desde, Instant hasta, VentanaHoraria franja, boolean exigePulso) {

    public VentanaLecturas {
        Objects.requireNonNull(desde, "desde");
        if (hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El fin de la ventana es anterior a su inicio.");
        }
    }

    /** Desde {@code desde} hasta la última lectura, todo el día, todos los orígenes. */
    public static VentanaLecturas desde(Instant desde) {
        return new VentanaLecturas(desde, null, null, false);
    }

    public static VentanaLecturas entre(Instant desde, Instant hasta) {
        return new VentanaLecturas(desde, hasta, null, false);
    }

    public VentanaLecturas conFranja(VentanaHoraria franja) {
        return new VentanaLecturas(desde, hasta, franja, exigePulso);
    }

    /** Sólo cuentan las lecturas que respondieron a un pulso. */
    public VentanaLecturas soloPulso() {
        return new VentanaLecturas(desde, hasta, franja, true);
    }
}
