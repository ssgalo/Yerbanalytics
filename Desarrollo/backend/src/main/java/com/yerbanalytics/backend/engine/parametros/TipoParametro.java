package com.yerbanalytics.backend.engine.parametros;

import java.math.BigDecimal;

/** Tipo de valor de un parámetro y su parseo validado contra la {@link DefinicionParametro}. */
public enum TipoParametro {

    NUMERO,
    /** {@link ValorParametro.Numero} con 0 decimales exigidos. */
    ENTERO,
    HORA,
    VENTANA_HORARIA;

    /**
     * Convierte un texto en un valor validando tipo, formato, rango y decimales.
     *
     * @throws ValorParametroInvalidoException con un mensaje apto para mostrar si algo no cumple
     */
    public ValorParametro parsear(String texto, DefinicionParametro def) {
        return switch (this) {
            case NUMERO, ENTERO -> parsearNumero(texto, def);
            case HORA -> {
                try {
                    yield ValorParametro.Hora.parse(texto);
                } catch (IllegalArgumentException e) {
                    throw new ValorParametroInvalidoException(e.getMessage());
                }
            }
            case VENTANA_HORARIA -> {
                try {
                    yield VentanaHoraria.parse(texto);
                } catch (IllegalArgumentException e) {
                    throw new ValorParametroInvalidoException(e.getMessage());
                }
            }
        };
    }

    private ValorParametro parsearNumero(String texto, DefinicionParametro def) {
        BigDecimal bd;
        try {
            bd = new BigDecimal(texto == null ? "" : texto.trim());
        } catch (NumberFormatException e) {
            throw new ValorParametroInvalidoException("Debe ser un número.");
        }
        double valor = bd.doubleValue();
        if (!Double.isFinite(valor)) {
            throw new ValorParametroInvalidoException("Debe ser un número.");
        }

        int escala = Math.max(0, bd.stripTrailingZeros().scale());
        if (this == ENTERO && escala > 0) {
            throw new ValorParametroInvalidoException("Debe ser un número entero.");
        }
        if (escala > def.decimales()) {
            throw new ValorParametroInvalidoException(def.decimales() == 0
                    ? "Debe ser un número entero."
                    : "Admite como máximo " + def.decimales() + " decimales.");
        }

        Double min = def.min();
        Double max = def.max();
        if ((min != null && valor < min) || (max != null && valor > max)) {
            throw new ValorParametroInvalidoException(mensajeRango(min, max, def.unidad()));
        }
        return new ValorParametro.Numero(valor);
    }

    private static String mensajeRango(Double min, Double max, String unidad) {
        String u = unidad == null || unidad.isBlank() ? "" : " " + unidad;
        if (min != null && max != null) {
            return "Debe estar entre " + fmt(min) + " y " + fmt(max) + u + ".";
        }
        return min != null ? "Debe ser como mínimo " + fmt(min) + u + "." : "Debe ser como máximo " + fmt(max) + u + ".";
    }

    private static String fmt(double d) {
        return new ValorParametro.Numero(d).canonico();
    }
}
