package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Parámetros de seguridad del motor: condiciones que frenan todo antes de actuar.
 *
 * <p>Note: {@code seguridad.antiguedad-max-lectura} is only the FLOOR of the stale threshold. The value the
 * engine actually uses is {@code max(floor, pulse interval + response window)}, resolved in
 * {@code SensingCadence} and overlaid by {@code CatalogoParametrosService.vigentes()}. The catalog API
 * ({@code catalogo()}) still shows the raw floor.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosSeguridad implements DefinicionParametro {

    ANTIGUEDAD_MAX_LECTURA("seguridad.antiguedad-max-lectura",
            "Antigüedad máxima de la lectura",
            "Si la última lectura de la zona es más vieja que esto, se considera que el sensor dejó de reportar y se bloquea la evaluación. Es sólo el piso: el umbral que usa el motor es el mayor entre este valor y un intervalo de sensado más la ventana de respuesta del pulso (4 h y 3 min con los valores de fábrica).",
            TipoParametro.ENTERO, "s", "90", 10.0, 600.0, 0, "motor-reglas: StaleSensorRule"),

    // --- S-03: rango físico posible de cada magnitud (reglas_v2 §4). Un valor fuera de rango no es una lectura. ---
    // Cada mínimo llega hasta un pivote y cada máximo parte de él (humedad 50, temperatura 25, pH 7, CE 5, luz 50):
    // así los rangos sólo se pueden editar hacia adentro y el mínimo nunca puede alcanzar al máximo.

    RANGO_HUM_MIN("seguridad.rango-hum-min",
            "Humedad: mínimo posible",
            "Una humedad (de sustrato o ambiental) por debajo de este valor no es física: la lectura se considera inválida.",
            TipoParametro.NUMERO, "%", "0", 0.0, 50.0, 0, "reglas_v2 §4 S-03"),

    RANGO_HUM_MAX("seguridad.rango-hum-max",
            "Humedad: máximo posible",
            "Una humedad (de sustrato o ambiental) por encima de este valor no es física: la lectura se considera inválida.",
            TipoParametro.NUMERO, "%", "100", 50.0, 100.0, 0, "reglas_v2 §4 S-03"),

    RANGO_TEMP_MIN("seguridad.rango-temp-min",
            "Temperatura: mínimo posible",
            "Una temperatura (del aire o del sustrato) por debajo de este valor se considera una lectura inválida.",
            TipoParametro.NUMERO, "°C", "-10", -40.0, 25.0, 1, "reglas_v2 §4 S-03"),

    RANGO_TEMP_MAX("seguridad.rango-temp-max",
            "Temperatura: máximo posible",
            "Una temperatura (del aire o del sustrato) por encima de este valor se considera una lectura inválida.",
            TipoParametro.NUMERO, "°C", "60", 25.0, 85.0, 1, "reglas_v2 §4 S-03"),

    RANGO_PH_MIN("seguridad.rango-ph-min",
            "pH: mínimo posible",
            "Un pH del sustrato por debajo de este valor se considera una lectura inválida.",
            TipoParametro.NUMERO, "", "3", 0.0, 7.0, 1, "reglas_v2 §4 S-03"),

    RANGO_PH_MAX("seguridad.rango-ph-max",
            "pH: máximo posible",
            "Un pH del sustrato por encima de este valor se considera una lectura inválida.",
            TipoParametro.NUMERO, "", "9", 7.0, 14.0, 1, "reglas_v2 §4 S-03"),

    RANGO_CE_MIN("seguridad.rango-ce-min",
            "Conductividad: mínimo posible",
            "Una conductividad eléctrica por debajo de este valor se considera una lectura inválida. Se compara ya convertida a dS/m.",
            TipoParametro.NUMERO, "dS/m", "0", 0.0, 5.0, 1, "reglas_v2 §4 S-03"),

    RANGO_CE_MAX("seguridad.rango-ce-max",
            "Conductividad: máximo posible",
            "Una conductividad eléctrica por encima de este valor se considera una lectura inválida. Se compara ya convertida a dS/m.",
            TipoParametro.NUMERO, "dS/m", "10", 5.0, 20.0, 1, "reglas_v2 §4 S-03"),

    RANGO_LUZ_MIN("seguridad.rango-luz-min",
            "Luz: mínimo posible",
            "Un porcentaje de luz (sensor LDR) por debajo de este valor se considera una lectura inválida.",
            TipoParametro.NUMERO, "%", "0", 0.0, 50.0, 0, "reglas_v2 §4 S-03"),

    RANGO_LUZ_MAX("seguridad.rango-luz-max",
            "Luz: máximo posible",
            "Un porcentaje de luz (sensor LDR) por encima de este valor se considera una lectura inválida.",
            TipoParametro.NUMERO, "%", "100", 50.0, 100.0, 0, "reglas_v2 §4 S-03"),

    RELECTURAS_INVALIDAS("seguridad.relecturas-invalidas",
            "Relecturas ante un valor inválido",
            "Veces que se vuelve a pedir la lectura, enseguida, cuando una métrica llega fuera de rango. Si todas fallan, esa métrica se descarta en el ciclo.",
            TipoParametro.ENTERO, "relecturas", "2", 1.0, 5.0, 0, "reglas_v2 §4 S-03");

    private final String clave;
    private final String etiqueta;
    private final String descripcion;
    private final TipoParametro tipo;
    private final String unidad;
    private final String fabrica;
    private final Double min;
    private final Double max;
    private final int decimales;
    private final String refSpec;

    @Override
    public FamiliaParametro familia() {
        return FamiliaParametro.SEGURIDAD;
    }
}
