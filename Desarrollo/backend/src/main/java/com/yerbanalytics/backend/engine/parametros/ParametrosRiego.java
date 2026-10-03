package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Parámetros de la rama de riego. */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosRiego implements DefinicionParametro {

    UMBRAL_HUMEDAD("riego.umbral-humedad",
            "Umbral de riego (humedad de sustrato)",
            "Por debajo de esta humedad de sustrato el sector necesita riego.",
            TipoParametro.NUMERO, "%", "42", 35.0, 60.0, 0, "motor-reglas: IrrigationRule"),
    TIEMPO_MAX_APERTURA("riego.tiempo-max-apertura",
            "Tiempo máximo de apertura de la electroválvula",
            "Duración máxima de un evento de riego.",
            TipoParametro.ENTERO, "s", "120", 10.0, 600.0, 0, "motor-reglas: IrrigationRule"),
    MAX_RIEGOS_24H("riego.max-riegos-24h",
            "Máximo de riegos en 24 h (límite de volumen)",
            "Con esta cantidad de riegos en 24 h el sector alcanzó su límite diario y no se riega más.",
            TipoParametro.ENTERO, "riegos", "2", 1.0, 10.0, 0, "motor-reglas: DailyVolumeLimitRule"),
    MAX_RIEGOS_24H_SECTOR("riego.max-riegos-24h-sector",
            "Máximo de riegos en 24 h (decisión de riego)",
            "Con esta cantidad de riegos en 24 h la regla de riego no vuelve a regar el sector.",
            TipoParametro.ENTERO, "riegos", "1", 1.0, 10.0, 0, "motor-reglas: IrrigationRule"),
    LLUVIA_PROBABILIDAD("riego.lluvia-probabilidad",
            "Probabilidad de lluvia que posterga el riego",
            "Con una probabilidad de lluvia igual o mayor a esta, se posterga el riego.",
            TipoParametro.NUMERO, "%", "60", 50.0, 95.0, 0, "motor-reglas: WeatherOverrideRule");

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
        return FamiliaParametro.RIEGO;
    }
}
