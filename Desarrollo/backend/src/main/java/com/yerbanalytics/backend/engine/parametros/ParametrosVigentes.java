package com.yerbanalytics.backend.engine.parametros;

import java.time.LocalTime;
import java.util.Map;

/**
 * Snapshot inmutable de los valores vigentes (clave → valor). Se toma una vez por evaluación
 * de sector, así todas las reglas de un ciclo ven lo mismo aunque alguien guarde a mitad.
 */
public final class ParametrosVigentes {

    private final Map<String, ValorParametro> valores;

    private ParametrosVigentes(Map<String, ValorParametro> valores) {
        this.valores = Map.copyOf(valores);
    }

    public static ParametrosVigentes de(Map<String, ValorParametro> valores) {
        return new ParametrosVigentes(valores);
    }

    public Map<String, ValorParametro> valores() {
        return valores;
    }

    public ValorParametro valor(String clave) {
        ValorParametro v = valores.get(clave);
        if (v == null) {
            throw new IllegalArgumentException("Parámetro inexistente: " + clave);
        }
        return v;
    }

    public double numero(String clave) {
        if (valor(clave) instanceof ValorParametro.Numero n) {
            return n.valor();
        }
        throw new IllegalStateException("El parámetro " + clave + " no es numérico.");
    }

    public LocalTime hora(String clave) {
        if (valor(clave) instanceof ValorParametro.Hora h) {
            return h.valor();
        }
        throw new IllegalStateException("El parámetro " + clave + " no es una hora.");
    }

    public VentanaHoraria ventana(String clave) {
        if (valor(clave) instanceof VentanaHoraria v) {
            return v;
        }
        throw new IllegalStateException("El parámetro " + clave + " no es una ventana horaria.");
    }

    public double numero(DefinicionParametro def) {
        return numero(def.clave());
    }

    public LocalTime hora(DefinicionParametro def) {
        return hora(def.clave());
    }

    public VentanaHoraria ventana(DefinicionParametro def) {
        return ventana(def.clave());
    }
}
