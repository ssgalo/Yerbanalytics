package com.yerbanalytics.backend.engine.parametros;

import java.time.LocalTime;
import java.util.function.Supplier;

/**
 * Lectura de parámetros para quien decide fuera de la cadena de reglas (el despacho, el servicio de aplicación): sólo
 * deja leer los que el componente declara en {@link ConsumidorParametros#parametros()}, la misma garantía que
 * {@code Evaluacion} da a las reglas. Así el campo {@code usadoPor} del catálogo no puede mentir.
 *
 * <p>La fuente es un {@link Supplier} y no un snapshot: el servicio que lo usa vive más que un ciclo y tiene que ver
 * el valor vigente de cada vez.
 */
public final class DeclaredParameters {

    private final ConsumidorParametros owner;
    private final Supplier<ParametrosVigentes> source;

    public DeclaredParameters(ConsumidorParametros owner, Supplier<ParametrosVigentes> source) {
        this.owner = owner;
        this.source = source;
    }

    /** Un snapshot ya tomado (p. ej. para una función pura que recibe los vigentes). */
    public static DeclaredParameters of(ConsumidorParametros owner, ParametrosVigentes vigentes) {
        return new DeclaredParameters(owner, () -> vigentes);
    }

    public double number(DefinicionParametro p) {
        return declared(p).numero(p);
    }

    public VentanaHoraria window(DefinicionParametro p) {
        return declared(p).ventana(p);
    }

    public LocalTime time(DefinicionParametro p) {
        return declared(p).hora(p);
    }

    private ParametrosVigentes declared(DefinicionParametro p) {
        if (!owner.parametros().contains(p)) {
            throw new ParametroNoDeclaradoException(owner.name(), p.clave());
        }
        return source.get();
    }
}
