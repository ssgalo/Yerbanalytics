package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Parámetros de la rama de riego: los de las reglas R-01…R-06 y la regla de ciclo, con los valores de
 * {@code reglas_v2} §5 y §11. Un parámetro compartido (umbral de riego, umbral crítico, ventana…) existe UNA
 * vez y lo declaran todas las reglas que lo leen.
 *
 * <p>Las claves de las reglas anteriores ({@code tiempo-max-apertura}, {@code max-riegos-24h},
 * {@code max-riegos-24h-sector}) ya no existen: el tiempo sale del volumen ÷ caudal y el riego en bucle lo evita
 * el ciclo de lectura. Un override viejo de esas claves se ignora (ver {@code migracion-reglas-riego.sql}).
 * {@code umbral-humedad} y {@code lluvia-probabilidad} pasaron de 42 / 60 a 45 / 70 de fábrica; un override
 * existente se respeta.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosRiego implements DefinicionParametro {

    UMBRAL_HUMEDAD("riego.umbral-humedad",
            "Umbral de riego (humedad de sustrato)",
            "Por debajo de esta humedad de sustrato el sector necesita riego.",
            TipoParametro.NUMERO, "%", "45", 35.0, 60.0, 0, "reglas_v2 §5 R-01 / §11 Riego"),
    LLUVIA_PROBABILIDAD("riego.lluvia-probabilidad",
            "Probabilidad de lluvia que posterga el riego",
            "Con una probabilidad de lluvia igual o mayor a esta (y los milímetros configurados), se posterga el riego.",
            TipoParametro.NUMERO, "%", "70", 50.0, 95.0, 0, "reglas_v2 §5 R-03 / §11 Riego"),

    // --- Reglas de riego v2 (reglas_v2 §5 y §11) ---

    UMBRAL_CRITICO("riego.umbral-critico",
            "Umbral crítico (humedad de sustrato)",
            "Por debajo de esta humedad el déficit es crítico: se riega con el volumen máximo, a cualquier hora y aunque se prevea lluvia.",
            TipoParametro.NUMERO, "%", "35", 25.0, 40.0, 0, "reglas_v2 §5 R-02 / §11 Riego"),
    HUMEDAD_OBJETIVO("riego.humedad-objetivo",
            "Humedad objetivo",
            "Humedad de sustrato a la que apunta el riego por déficit: el volumen es (objetivo − humedad) × litros por punto.",
            TipoParametro.NUMERO, "%", "65", 55.0, 75.0, 0, "reglas_v2 §5 R-01 / §11 Riego"),
    LITROS_POR_PUNTO("riego.litros-por-punto",
            "Litros por punto de humedad",
            "Litros de agua que se aplican por cada punto de humedad que le falta al sustrato para llegar al objetivo.",
            TipoParametro.NUMERO, "L/punto", "0.2", 0.1, 0.5, 2, "reglas_v2 §5 R-01 / §11 Riego"),
    VOLUMEN_MAX_EVENTO("riego.volumen-max-evento",
            "Volumen máximo por evento de riego",
            "Tope de agua de un solo riego por sector. Es también el volumen del riego por déficit crítico.",
            TipoParametro.NUMERO, "L", "6", 3.0, 10.0, 1, "reglas_v2 §5 R-01 y R-02 / §11 Riego"),
    CAUDAL_EMISOR("riego.caudal-emisor",
            "Caudal del emisor",
            "Caudal real del microaspersor de un sector. Con él se calcula cuánto tiempo abrir la válvula para entregar el volumen.",
            TipoParametro.NUMERO, "L/h", "30", 5.0, 120.0, 1, "reglas_v2 §1.2 / §11 Riego"),
    SATURACION_BLOQUEO("riego.saturacion-bloqueo",
            "Saturación que bloquea el riego",
            "Con esta humedad de sustrato o más, el sustrato está saturado y no se riega.",
            TipoParametro.NUMERO, "%", "75", 65.0, 85.0, 0, "reglas_v2 §5 R-04 / §11 Riego"),
    SATURACION_ALERTA("riego.saturacion-alerta",
            "Saturación que genera alerta",
            "Con esta humedad de sustrato o más, además de bloquear el riego se avisa del riesgo de asfixia radicular y hongos.",
            TipoParametro.NUMERO, "%", "80", 65.0, 85.0, 0, "reglas_v2 §5 R-04 / §11 Riego"),
    VENTANA_NORMAL("riego.ventana-normal",
            "Ventana horaria de riego normal",
            "Franja del día en que se permite el riego por déficit. Fuera de ella sólo puede regar el déficit crítico. La lectura de la hora de fin todavía entra.",
            TipoParametro.VENTANA_HORARIA, "", "06:00-18:00", null, null, 0, "reglas_v2 §5 R-05 / §11 Riego"),
    LLUVIA_MM("riego.lluvia-mm",
            "Lluvia prevista que posterga el riego",
            "Milímetros de lluvia acumulada prevista en la ventana. Se posterga el riego sólo si se alcanza también la probabilidad configurada.",
            TipoParametro.NUMERO, "mm", "5", 2.0, 20.0, 1, "reglas_v2 §5 R-03 / §11 Riego"),
    LLUVIA_VENTANA("riego.lluvia-ventana",
            "Ventana del pronóstico de lluvia",
            "Cantidad de horas hacia adelante del pronóstico que se consideran para posponer el riego.",
            TipoParametro.ENTERO, "h", "4", 2.0, 12.0, 0, "reglas_v2 §5 R-03 / §11 Riego"),
    PAUSA_TRAS_APLICACION("riego.pausa-tras-aplicacion",
            "Pausa de riego tras una aplicación",
            "Horas sin regar un sector después de aplicarle fertilizante o fitosanitario, para no lavar el producto.",
            TipoParametro.NUMERO, "h", "6", 2.0, 24.0, 0, "reglas_v2 §5 R-06 / §11 Riego"),
    EXCEPTUADO_BLOQUEO("riego.exceptuado-bloqueo",
            "Intervalo mínimo entre riegos por déficit crítico",
            "Horas que deben pasar entre dos riegos por déficit crítico de un mismo sector. Evita regar sin fin con un sensor que marca seco de forma falsa.",
            TipoParametro.NUMERO, "h", "12", 6.0, 24.0, 0, "reglas_v2 §4 y §11 Riego"),
    SECTORES_SIMULTANEOS("riego.sectores-simultaneos",
            "Sectores regando a la vez por macro-zona",
            "Máximo de electroválvulas abiertas al mismo tiempo en una macro-zona: los demás sectores esperan su turno, en orden.",
            TipoParametro.ENTERO, "sectores", "10", 1.0, 100.0, 0, "reglas_v2 §2 y §11 Riego");

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
