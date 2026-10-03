package com.yerbanalytics.backend.engine.parametros;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Ventana horaria {@code [desde, hasta)}: incluye el inicio y excluye el fin. Si {@code desde}
 * es posterior a {@code hasta} cruza la medianoche, lo que permite expresar "fuera de 06–18"
 * como {@code 18:00-06:00} sin un tipo extra.
 */
public record VentanaHoraria(LocalTime desde, LocalTime hasta) implements ValorParametro {

    private static final String FORMATO = "Formato esperado HH:mm-HH:mm (con inicio y fin distintos).";

    public VentanaHoraria {
        if (desde == null || hasta == null || desde.equals(hasta)) {
            throw new IllegalArgumentException(FORMATO);
        }
    }

    public static VentanaHoraria parse(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException(FORMATO);
        }
        String[] partes = texto.trim().split("-", -1);
        if (partes.length != 2) {
            throw new IllegalArgumentException(FORMATO);
        }
        try {
            return new VentanaHoraria(
                    ValorParametro.parseHoraEstricta(partes[0]),
                    ValorParametro.parseHoraEstricta(partes[1]));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(FORMATO);
        }
    }

    public boolean contiene(LocalTime t) {
        if (desde.isBefore(hasta)) {
            return !t.isBefore(desde) && t.isBefore(hasta);
        }
        return !t.isBefore(desde) || t.isBefore(hasta);
    }

    /**
     * Como {@link #contiene} pero cerrada en su hora de fin al minuto: la lectura de las 18:00
     * todavía entra en la ventana {@code 06:00-18:00} ({@code 18:00:59} adentro, {@code 18:01:00}
     * afuera). La hora de inicio ya estaba incluida.
     */
    public boolean contieneHastaElMinuto(LocalTime t) {
        return contiene(t) || t.truncatedTo(ChronoUnit.MINUTES).equals(hasta);
    }

    @Override
    public String canonico() {
        return desde.format(FMT_HH_MM) + "-" + hasta.format(FMT_HH_MM);
    }
}
