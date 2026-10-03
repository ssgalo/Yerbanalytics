package com.yerbanalytics.backend.engine.traza;

import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametroNoDeclaradoException;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.parametros.VentanaHoraria;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lo que una regla usa para decidir: lee los parámetros vigentes y compara registrando cada
 * comparación. Decidir y registrar son el mismo gesto, así la traza no puede quedar incompleta.
 *
 * <p>Se crea una por regla por ciclo, la usa un solo hilo y se descarta: es mutable a propósito
 * y NO es thread-safe. Sólo deja leer los parámetros que la regla declaró en
 * {@link Rule#parametros()}; cualquier otro lanza {@link ParametroNoDeclaradoException}.
 */
public final class Evaluacion {

    private final String regla;
    private final Set<String> declarados;
    private final ParametrosVigentes vigentes;
    private final List<Comparacion> comparaciones = new ArrayList<>(3);

    public Evaluacion(Rule rule, ParametrosVigentes vigentes) {
        this.regla = rule.name();
        this.declarados = rule.parametros().stream()
                .map(DefinicionParametro::clave)
                .collect(Collectors.toUnmodifiableSet());
        this.vigentes = vigentes;
    }

    public double numero(DefinicionParametro p) {
        return vigentes.numero(declarado(p));
    }

    public LocalTime hora(DefinicionParametro p) {
        return vigentes.hora(declarado(p));
    }

    public VentanaHoraria ventana(DefinicionParametro p) {
        return vigentes.ventana(declarado(p));
    }

    /**
     * Compara {@code recibido} contra el parámetro y registra la comparación. Sin dato
     * ({@code recibido == null}) devuelve {@code false} y queda como {@code SIN_DATO}.
     */
    public boolean comparar(String etiqueta, Double recibido, Operador op, DefinicionParametro umbral) {
        double valorUmbral = numero(umbral);
        boolean cumple = recibido != null && op.cumple(Double.compare(recibido, valorUmbral));
        comparaciones.add(new Comparacion(etiqueta, umbral.clave(), recibido, op, valorUmbral,
                umbral.unidad(), true, resultado(recibido, cumple)));
        return cumple;
    }

    /**
     * Compara contra una condición que no es configurable (p. ej. {@code estado == critical}).
     * Queda en la traza con {@code configurable = false} y sin clave: se ve que existe y que no se edita.
     * Con números admite todos los operadores; con otros tipos sólo {@link Operador#EQ}.
     */
    public boolean compararFijo(String etiqueta, Object recibido, Operador op, Object umbral) {
        boolean cumple = recibido != null && aplicar(recibido, op, umbral);
        comparaciones.add(new Comparacion(etiqueta, null, recibido, op, umbral, "", false,
                resultado(recibido, cumple)));
        return cumple;
    }

    /** Comparaciones registradas hasta ahora, en orden; vista de sólo lectura. */
    public List<Comparacion> comparaciones() {
        return Collections.unmodifiableList(comparaciones);
    }

    private DefinicionParametro declarado(DefinicionParametro p) {
        if (!declarados.contains(p.clave())) {
            throw new ParametroNoDeclaradoException(regla, p.clave());
        }
        return p;
    }

    private static ResultadoComparacion resultado(Object recibido, boolean cumple) {
        if (recibido == null) {
            return ResultadoComparacion.SIN_DATO;
        }
        return cumple ? ResultadoComparacion.CUMPLE : ResultadoComparacion.NO_CUMPLE;
    }

    private static boolean aplicar(Object recibido, Operador op, Object umbral) {
        if (recibido instanceof Number r && umbral instanceof Number u) {
            return op.cumple(Double.compare(r.doubleValue(), u.doubleValue()));
        }
        if (op != Operador.EQ) {
            throw new IllegalArgumentException("El operador " + op + " sólo aplica a números.");
        }
        return recibido.equals(umbral);
    }
}
