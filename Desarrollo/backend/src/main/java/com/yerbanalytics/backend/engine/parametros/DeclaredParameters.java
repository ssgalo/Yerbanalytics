package com.yerbanalytics.backend.engine.parametros;

import java.time.LocalTime;
import java.util.function.Supplier;

/**
 * Lectura de parámetros para quien decide fuera de la cadena de reglas (el despacho, el servicio de aplicación): sólo
 * deja leer los que el componente declara, propios ({@link ConsumidorParametros#parametros()}) o de otra pieza
 * ({@link ConsumidorParametros#parametrosDe()}), la misma garantía que {@code Evaluacion} da a las reglas. Lee siempre la
 * vista del dueño, donde las claves de otra pieza ya vienen con el valor de la copia de esa pieza.
 *
 * <p>La fuente es un {@link Supplier} y no un snapshot: el servicio que lo usa vive más que un ciclo y tiene que ver
 * el valor vigente de cada vez.
 */
public final class DeclaredParameters {

    private final ConsumidorParametros owner;
    private final Supplier<ParametrosVigentes> source;

    /** Fuente con todas las piezas: se toma la vista del dueño en cada lectura. */
    public DeclaredParameters(ConsumidorParametros owner, Supplier<ParametrosPorPieza> source) {
        this.owner = owner;
        this.source = () -> source.get().de(owner);
    }

    private DeclaredParameters(ConsumidorParametros owner, ParametrosVigentes vista) {
        this.owner = owner;
        this.source = () -> vista;
    }

    /** La vista ya tomada del dueño (p. ej. para una función pura que la recibe). */
    public static DeclaredParameters of(ConsumidorParametros owner, ParametrosVigentes vista) {
        return new DeclaredParameters(owner, vista);
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

    /** {@code true} si el componente declara el parámetro, propio o de otra pieza. */
    public static boolean declares(ConsumidorParametros owner, DefinicionParametro p) {
        return owner.parametros().contains(p) || owner.parametrosDe().containsKey(p);
    }

    private ParametrosVigentes declared(DefinicionParametro p) {
        if (!declares(owner, p)) {
            throw new ParametroNoDeclaradoException(owner.name(), p.clave());
        }
        return source.get();
    }
}
