package com.yerbanalytics.backend.engine.parametros;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Snapshot inmutable de los valores vigentes de TODAS las piezas (rediseno-motor-reglas D-1): cada pieza que lee
 * parámetros tiene su copia, y {@link #de(ConsumidorParametros)} devuelve su vista ({@link ParametrosVigentes}, la misma
 * API de siempre). En la vista de una pieza ya están resueltas las claves que lee de otra (D-2) y el umbral efectivo de
 * antigüedad de la lectura.
 *
 * <p>Una pieza que el catálogo no conoce (p. ej. una regla armada en un test) ve la vista base: fábrica con el umbral
 * efectivo superpuesto. Se toma una vez por evaluación, así todas las reglas de un ciclo ven lo mismo aunque alguien
 * guarde a mitad.
 */
public final class ParametrosPorPieza {

    private final Map<String, ParametrosVigentes> vistas;
    private final ParametrosVigentes base;
    private final String huella;

    private ParametrosPorPieza(Map<String, ParametrosVigentes> vistas, ParametrosVigentes base) {
        this.vistas = Map.copyOf(vistas);
        this.base = base;
        // Inmutable: se calcula una vez. Ordenado por nombre para que no dependa del orden de inserción.
        Map<String, Map<String, ValorParametro>> todo = new TreeMap<>();
        this.vistas.forEach((pieza, vista) -> todo.put(pieza, vista.valores()));
        this.huella = Integer.toHexString((todo.hashCode() * 31) + base.valores().hashCode());
    }

    /**
     * @param vistas vista de cada pieza conocida, por {@link ConsumidorParametros#name()}
     * @param base   lo que ve una pieza que no figura en {@code vistas}
     */
    public static ParametrosPorPieza of(Map<String, ParametrosVigentes> vistas, ParametrosVigentes base) {
        return new ParametrosPorPieza(new LinkedHashMap<>(vistas), base);
    }

    /** Todas las piezas ven los mismos valores (para tests y para funciones puras que no distinguen piezas). */
    public static ParametrosPorPieza uniforme(ParametrosVigentes valores) {
        return new ParametrosPorPieza(Map.of(), valores);
    }

    /** La vista de una pieza. */
    public ParametrosVigentes de(ConsumidorParametros pieza) {
        return de(pieza.name());
    }

    /** La vista de una pieza por su nombre técnico; la base si el catálogo no la conoce. */
    public ParametrosVigentes de(String pieza) {
        return vistas.getOrDefault(pieza, base);
    }

    /**
     * Huella de todos los valores de todas las piezas: dos trazas con la misma huella se evaluaron contra los mismos
     * umbrales, pieza por pieza.
     */
    public String huella() {
        return huella;
    }
}
