package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.engine.parametros.ParametrosSeguridad;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * S-03: ¿cada valor de la lectura cae dentro del rango físico posible de su magnitud? Pura (sin Spring ni estado):
 * recibe los rangos vigentes y dice cuáles métricas están fuera. Qué hacer con ellas (relectura, descarte, alerta)
 * es de {@link ReadingValidationGuard}.
 *
 * <p>Los bordes pertenecen al rango. La conductividad se compara ya convertida a dS/m
 * ({@link ReadingMetrics}). N, P y K no tienen rango en la v2, así que nunca se invalidan. Un valor no finito
 * (NaN, infinito) está siempre fuera de rango.
 */
public final class ReadingValidator {

    /** Rango físico de una magnitud: los dos parámetros del catálogo que lo definen. */
    private record Range(ParametrosSeguridad min, ParametrosSeguridad max) {
        boolean contains(ParametrosVigentes p, double value) {
            return Double.isFinite(value) && value >= p.numero(min) && value <= p.numero(max);
        }
    }

    private static final Range HUMIDITY = new Range(ParametrosSeguridad.RANGO_HUM_MIN, ParametrosSeguridad.RANGO_HUM_MAX);
    private static final Range TEMPERATURE = new Range(ParametrosSeguridad.RANGO_TEMP_MIN, ParametrosSeguridad.RANGO_TEMP_MAX);

    /** Qué rango controla cada métrica, por su clave del contrato. Lo que no figura no se valida. */
    private static final Map<String, Range> RANGE_BY_METRIC = Map.of(
            "humSus", HUMIDITY,
            "humAmb", HUMIDITY,
            "temp", TEMPERATURE,
            "tempSuelo", TEMPERATURE,
            "phSuelo", new Range(ParametrosSeguridad.RANGO_PH_MIN, ParametrosSeguridad.RANGO_PH_MAX),
            "ce", new Range(ParametrosSeguridad.RANGO_CE_MIN, ParametrosSeguridad.RANGO_CE_MAX),
            "uv", new Range(ParametrosSeguridad.RANGO_LUZ_MIN, ParametrosSeguridad.RANGO_LUZ_MAX));

    private ReadingValidator() {
    }

    /** Claves de las métricas de la lectura cuyo valor está fuera de su rango físico. */
    public static Set<String> invalidMetrics(ReadingMetrics reading, ParametrosVigentes parameters) {
        Set<String> invalid = new LinkedHashSet<>();
        reading.asMap().forEach((key, value) -> {
            Range range = RANGE_BY_METRIC.get(key);
            if (range != null && !range.contains(parameters, value)) {
                invalid.add(key);
            }
        });
        return invalid;
    }
}
