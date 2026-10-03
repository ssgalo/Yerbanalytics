package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.riego.CalculoRiego;
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
 *   <li>un valor de fábrica no cumple su propio tipo o rango, o un rango tiene mínimo mayor que máximo;</li>
 *   <li>los valores de fábrica violan una restricción cruzada;</li>
 *   <li>una restricción cruzada o una regla refiere a una clave que no existe.</li>
 * </ul>
 * No conoce los valores vigentes: eso es {@link CatalogoParametrosService}.
 */
@Component
public class CatalogoParametros {

    /** {@code familia.nombre-en-kebab-case}. */
    private static final Pattern CLAVE = Pattern.compile("[a-z]+\\.[a-z0-9]+(-[a-z0-9]+)*");

    /** Definiciones reales, una enum por familia. Sumar una familia nueva es agregarla acá. */
    static List<DefinicionParametro> definicionesReales() {
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

    /**
     * Constructor de Spring: definiciones reales y todo lo que lee parámetros (las reglas registradas
     * y quien ejecuta sin ser regla, como el despacho de riego).
     */
    @Autowired
    public CatalogoParametros(List<ConsumidorParametros> consumidores) {
        this(definicionesReales(), restriccionesReales(), consumidores);
    }

    /** Restricciones cruzadas de las familias reales (riego: {@code reglas_v2} §11 y límite de la válvula). */
    static List<RestriccionCruzada> restriccionesReales() {
        return List.of(
                new RestriccionCruzada(
                        List.of("riego.umbral-critico", "riego.umbral-humedad"),
                        v -> v.numero("riego.umbral-critico") < v.numero("riego.umbral-humedad"),
                        "El umbral crítico debe ser menor que el umbral de riego."),
                new RestriccionCruzada(
                        List.of("riego.umbral-humedad", "riego.humedad-objetivo"),
                        v -> v.numero("riego.umbral-humedad") < v.numero("riego.humedad-objetivo"),
                        "El umbral de riego debe ser menor que la humedad objetivo."),
                new RestriccionCruzada(
                        List.of("riego.saturacion-bloqueo", "riego.saturacion-alerta"),
                        v -> v.numero("riego.saturacion-bloqueo") <= v.numero("riego.saturacion-alerta"),
                        "El bloqueo por saturación no puede superar la alerta de saturación."),
                new RestriccionCruzada(
                        List.of("riego.volumen-max-evento", "riego.caudal-emisor"),
                        v -> CalculoRiego.cabeEnLaValvula(v.numero("riego.volumen-max-evento"),
                                v.numero("riego.caudal-emisor")),
                        "Con ese caudal, el volumen máximo no se alcanza a regar dentro del límite de la válvula."));
    }

    public CatalogoParametros(List<DefinicionParametro> definiciones,
                              List<RestriccionCruzada> restricciones,
                              List<? extends ConsumidorParametros> consumidores) {
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
            if (d.min() != null && d.max() != null && d.min() > d.max()) {
                throw new IllegalStateException("Rango inválido en '" + clave + "': el mínimo (" + d.min()
                        + ") supera al máximo (" + d.max() + ").");
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

        ParametrosVigentes deFabrica = ParametrosVigentes.de(fab);
        for (RestriccionCruzada r : restricciones) {
            if (!r.cumple(deFabrica)) {
                throw new IllegalStateException("Los valores de fábrica violan la restricción: " + r.mensaje());
            }
        }

        // Por prioridad (estable): así reglas() y usadoPor() salen en el orden en que corre el motor.
        // Los consumidores que no son reglas (el despacho de riego) van después, en el orden dado.
        List<Rule> ordenadas = consumidores.stream().filter(Rule.class::isInstance).map(Rule.class::cast)
                .sorted(Comparator.comparingInt(Rule::priority)).toList();
        List<ConsumidorParametros> lectores = new ArrayList<>(ordenadas);
        consumidores.stream().filter(c -> !(c instanceof Rule)).forEach(lectores::add);
        Map<String, List<String>> usos = new LinkedHashMap<>();
        for (ConsumidorParametros lector : lectores) {
            for (DefinicionParametro p : lector.parametros()) {
                if (!mapa.containsKey(p.clave())) {
                    throw new IllegalStateException("'" + lector.name()
                            + "' declara un parámetro inexistente en el catálogo: '" + p.clave() + "'.");
                }
                usos.computeIfAbsent(p.clave(), k -> new ArrayList<>()).add(lector.name());
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

    /** Nombres de quienes declaran el parámetro: primero las reglas por prioridad, después los demás. Vacía si nadie. */
    public List<String> usadoPor(String clave) {
        return usadoPor.getOrDefault(clave, List.of());
    }

    /** Restricciones cruzadas que el conjunto de valores dado NO cumple. */
    public List<RestriccionCruzada> violaciones(ParametrosVigentes valores) {
        return restricciones.stream().filter(r -> !r.cumple(valores)).toList();
    }
}
