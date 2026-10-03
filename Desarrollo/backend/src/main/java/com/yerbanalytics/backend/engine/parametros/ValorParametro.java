package com.yerbanalytics.backend.engine.parametros;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Valor de un parámetro de regla. Interface sellada: sumar un tipo nuevo (p. ej. una lista de
 * ventanas) es agregar un permitido, sin tocar los existentes.
 *
 * <p>Horas, minutos y milímetros no tienen tipo propio: son {@link Numero} con su unidad,
 * porque se comparan igual. {@code ENTERO} tampoco: es un {@link Numero} con 0 decimales.
 */
public sealed interface ValorParametro permits ValorParametro.Numero, ValorParametro.Hora, VentanaHoraria {

    /** Formato HH:mm estricto (dos dígitos). */
    Pattern HH_MM = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    DateTimeFormatter FMT_HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** Formato estable con el que se persiste y se expone el valor ({@code "42"}, {@code "06:00-18:00"}). */
    String canonico();

    /** Parsea un {@code HH:mm} estricto; lanza {@link IllegalArgumentException} si no lo es. */
    static LocalTime parseHoraEstricta(String texto) {
        if (texto == null || !HH_MM.matcher(texto.trim()).matches()) {
            throw new IllegalArgumentException("Formato esperado HH:mm.");
        }
        return LocalTime.parse(texto.trim(), FMT_HH_MM);
    }

    record Numero(double valor) implements ValorParametro {
        @Override
        public String canonico() {
            BigDecimal bd = BigDecimal.valueOf(valor).stripTrailingZeros();
            return (bd.scale() < 0 ? bd.setScale(0) : bd).toPlainString();
        }
    }

    record Hora(LocalTime valor) implements ValorParametro {
        public static Hora parse(String texto) {
            return new Hora(parseHoraEstricta(texto));
        }

        @Override
        public String canonico() {
            return valor.format(FMT_HH_MM);
        }
    }
}
