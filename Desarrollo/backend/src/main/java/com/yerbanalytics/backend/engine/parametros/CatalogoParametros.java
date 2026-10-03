package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.engine.Rule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Catálogo único de parámetros de reglas: junta las definiciones de todas las familias y las
 * reglas que las usan. Es inmutable y <b>falla al construirse</b> (la aplicación no arranca) si:
 * <ul>
 *   <li>hay claves duplicadas, mal formadas o sin el prefijo de su familia;</li>
 *   <li>un valor de fábrica no cumple su propio tipo o rango;</li>
 *   <li>una restricción cruzada o una regla refiere a una clave que no existe.</li>
 * </ul>
 * No conoce los valores vigentes: eso es {@link CatalogoParametrosService}.
 */
@Component
public class CatalogoParametros {

    /** {@code familia.nombre-en-kebab-case}. */
    private static final Pattern CLAVE = Pattern.compile("[a-z]+\\.[a-z0-9]+(-[a-z0-9]+)*");

    /** Definiciones reales, una enum por familia. Sumar una familia nueva es agregarla acá. */
    private static List<DefinicionParametro> definicionesReales() {
        List<DefinicionParametro> todas = new ArrayList<>();
        for (DefinicionParametro[] familia : new DefinicionParametro[][]{
                ParametrosSeguridad.values(),
                ParametrosRiego.values(),
                ParametrosInsumo.values(),
                ParametrosMediasombra.values(),
                ParametrosDiagnostico.values()}) {
            todas.addAll(List.of(familia));
        }
        return todas;
    }

    private final Map<String, DefinicionParametro> porClave;
    private final List<DefinicionParametro> definicionesEnOrden;
    private final List<RestriccionCruzada> restricciones;
    private final List<Rule> reglas;
    private final Map<String, ValorParametro> fabricas;
    private final Map<String, List<String>> usadoPor;

    /** Constructor de Spring: definiciones reales y todas las reglas registradas. */
    @Autowired
    public CatalogoParametros(List<Rule> reglas) {
        this(definicionesReales(), restriccionesReales(), reglas);
    }

    /** Restricciones cruzadas de las familias reales: hoy ninguna (se suman con las reglas v2). */
    private static List<RestriccionCruzada> restriccionesReales() {
        return List.of();
    }

    public CatalogoParametros(List<DefinicionParametro> definiciones,
                              List<RestriccionCruzada> restricciones,
                              List<Rule> reglas) {
        Map<String, DefinicionParametro> mapa = new LinkedHashMap<>();
        Map<String, ValorParametro> fab = new LinkedHashMap<>();
        for (DefinicionParametro d : definiciones) {
            String clave = d.clave();
            if (mapa.containsKey(clave)) {
                throw new IllegalStateException("Parámetro duplicado en el catálogo: '" + clave + "'.");
            }
            if (!CLAVE.matcher(clave).matches() || !clave.startsWith(d.familia().name().toLowerCase() + ".")) {
                throw new IllegalStateException("Clave inválida '" + clave + "': debe ser '"
                        + d.familia().name().toLowerCase() + ".nombre-en-kebab-case'.");
            }
            try {
                fab.put(clave, d.tipo().parsear(d.fabrica(), d));
            } catch (ValorParametroInvalidoException e) {
                throw new IllegalStateException("Valor de fábrica inválido en '" + clave + "' ('"
                        + d.fabrica() + "'): " + e.getMessage(), e);
            }
            mapa.put(clave, d);
        }

        for (RestriccionCruzada r : restricciones) {
            for (String clave : r.claves()) {
                if (!mapa.containsKey(clave)) {
                    throw new IllegalStateException("La restricción '" + r.mensaje()
                            + "' refiere a un parámetro inexistente: '" + clave + "'.");
                }
            }
        }

        // Por prioridad (estable): así reglas() y usadoPor() salen en el orden en que corre el motor.
        List<Rule> ordenadas = reglas.stream().sorted(Comparator.comparingInt(Rule::priority)).toList();
        Map<String, List<String>> usos = new LinkedHashMap<>();
        for (Rule regla : ordenadas) {
            for (DefinicionParametro p : regla.parametros()) {
                if (!mapa.containsKey(p.clave())) {
                    throw new IllegalStateException("La regla '" + regla.name()
                            + "' declara un parámetro inexistente en el catálogo: '" + p.clave() + "'.");
                }
                usos.computeIfAbsent(p.clave(), k -> new ArrayList<>()).add(regla.name());
            }
        }

        this.porClave = Map.copyOf(mapa);
        this.definicionesEnOrden = List.copyOf(mapa.values());
        this.restricciones = List.copyOf(restricciones);
        this.reglas = ordenadas;
        this.fabricas = Map.copyOf(fab);
        this.usadoPor = Map.copyOf(usos);
    }

    public List<DefinicionParametro> definiciones() {
        return definicionesEnOrden;
    }

    public Optional<DefinicionParametro> definicion(String clave) {
        return Optional.ofNullable(porClave.get(clave));
    }

    public List<Rule> reglas() {
        return reglas;
    }

    public List<RestriccionCruzada> restricciones() {
        return restricciones;
    }

    /** Valores de fábrica de todos los parámetros. */
    public ParametrosVigentes fabricas() {
        return ParametrosVigentes.de(fabricas);
    }

    /** Nombres de las reglas que declaran el parámetro, por prioridad. Vacía si ninguna. */
    public List<String> usadoPor(String clave) {
        return usadoPor.getOrDefault(clave, List.of());
    }

    /** Restricciones cruzadas que el conjunto de valores dado NO cumple. */
    public List<RestriccionCruzada> violaciones(ParametrosVigentes valores) {
        return restricciones.stream().filter(r -> !r.cumple(valores)).toList();
    }
}
