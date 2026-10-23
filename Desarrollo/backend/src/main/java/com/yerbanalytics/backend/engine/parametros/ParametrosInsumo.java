package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Parámetros de la rama de insumos: la dosis, los intervalos y las precondiciones de una aplicación (reglas_v2 §8).
 *
 * <p>El máximo de las dosis (50 ml) es el tope que acepta el firmware por comando ({@code LIMITE_BOMBA_ML_MAX},
 * {@code ContratoNodo.BOMBA_ML_MAX}). Va como literal porque el frontend lee este archivo para no desalinearse; que
 * coincida con el contrato lo verifica {@code ParametrosRealesTest}.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosInsumo implements DefinicionParametro {

    MAX_DOSIS_24H("insumo.max-dosis-24h",
            "Máximo de dosis en 24 h",
            "Con esta cantidad de dosificaciones en 24 h el sector alcanzó su límite y no se le aplican más insumos.",
            TipoParametro.ENTERO, "dosis", "1", 1.0, 10.0, 0, "motor-reglas: DailyDoseLimitRule"),

    FERTILIZER_DOSE("insumo.dosis-fertilizante",
            "Dosis de fertilizante por aplicación",
            "Mililitros de fertilizante por aplicación. El backend se lo manda al nodo en el comando y el firmware corta solo; no acepta más de 50 ml por comando.",
            TipoParametro.ENTERO, "ml", "10", 1.0, 50.0, 0,
            "reglas_v2 §8.1 (acotada por el firmware: LIMITE_BOMBA_ML_MAX)"),
    FUNGICIDE_DOSE("insumo.dosis-fitosanitario",
            "Dosis de fitosanitario por aplicación",
            "Mililitros de fitosanitario por aplicación. El backend se lo manda al nodo en el comando y el firmware corta solo; no acepta más de 50 ml por comando.",
            TipoParametro.ENTERO, "ml", "10", 1.0, 50.0, 0,
            "reglas_v2 §8.1 (acotada por el firmware: LIMITE_BOMBA_ML_MAX)"),
    FERTILIZER_INTERVAL("insumo.intervalo-fertilizante",
            "Intervalo mínimo entre fertilizaciones",
            "Días que deben pasar entre dos aplicaciones de fertilizante en un mismo sector.",
            TipoParametro.NUMERO, "d", "5", 3.0, 14.0, 0, "reglas_v2 §8.2 P-D2"),
    FUNGICIDE_INTERVAL("insumo.intervalo-fitosanitario",
            "Intervalo mínimo entre fitosanitarios",
            "Días que deben pasar entre dos aplicaciones de fitosanitario en un mismo sector.",
            TipoParametro.NUMERO, "d", "7", 5.0, 21.0, 0, "reglas_v2 §8.2 P-D2"),
    MORNING_WINDOW("insumo.ventana-manana",
            "Ventana de aplicación de la mañana",
            "Franja de la mañana en que se permite aplicar insumos. La hora de fin queda afuera.",
            TipoParametro.VENTANA_HORARIA, "", "07:00-10:00", null, null, 0, "reglas_v2 §8.2 P-D3"),
    AFTERNOON_WINDOW("insumo.ventana-tarde",
            "Ventana de aplicación de la tarde",
            "Franja de la tarde en que se permite aplicar insumos. La hora de fin queda afuera.",
            TipoParametro.VENTANA_HORARIA, "", "17:00-19:00", null, null, 0, "reglas_v2 §8.2 P-D3"),
    MAX_APPLICATION_TEMP("insumo.temp-max-aplicar",
            "Temperatura máxima para aplicar",
            "Con más temperatura que esta (última lectura o pronóstico) no se aplican insumos.",
            TipoParametro.NUMERO, "°C", "30", 25.0, 32.0, 0, "reglas_v2 §8.2 P-D3"),
    MIN_APPLICATION_MOISTURE("insumo.humedad-min-aplicar",
            "Humedad de sustrato mínima para aplicar",
            "Con menos humedad de sustrato que esta no se aplican insumos, salvo que el sector se haya regado después de la lectura.",
            TipoParametro.NUMERO, "%", "45", 35.0, 60.0, 0, "reglas_v2 §8.2 P-D5"),
    RAIN_BLOCKS_MM("insumo.lluvia-impide-mm",
            "Lluvia prevista que impide aplicar",
            "Milímetros de lluvia acumulada prevista en la ventana del pronóstico que impiden aplicar insumos.",
            TipoParametro.NUMERO, "mm", "5", 2.0, 20.0, 1, "reglas_v2 §8.2 P-D4"),
    RAIN_BLOCKS_HOURS("insumo.lluvia-impide-horas",
            "Ventana del pronóstico de lluvia para aplicar",
            "Cantidad de horas hacia adelante del pronóstico que se consideran para impedir una aplicación.",
            TipoParametro.ENTERO, "h", "6", 2.0, 12.0, 0, "reglas_v2 §8.2 P-D4"),
    REENTRY_HOURS("insumo.reingreso-horas",
            "Reingreso restringido tras un fitosanitario",
            "Horas en que el sector queda con reingreso restringido después de aplicarle un fitosanitario.",
            TipoParametro.NUMERO, "h", "24", 12.0, 72.0, 0, "reglas_v2 §8.3"),
    STOCK_WARNING("insumo.stock-aviso",
            "Nivel de tanque que avisa reponer",
            "Por debajo de este porcentaje de su capacidad, el tanque de un insumo genera un aviso para reponerlo.",
            TipoParametro.NUMERO, "%", "20", 10.0, 50.0, 0, "reglas_v2 §8.3"),
    PENDING_EXPIRY_DAYS("insumo.pendiente-vence-dias",
            "Vencimiento de una aplicación diferida",
            "Días que una aplicación diferida espera una ventana apta antes de descartarse. Propuesta del equipo: la v2 no fija un plazo.",
            TipoParametro.ENTERO, "d", "7", 1.0, 30.0, 0, "add-dosificacion-base D-5");

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
        return FamiliaParametro.INSUMO;
    }
}
