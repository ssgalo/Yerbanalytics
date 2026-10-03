package com.yerbanalytics.backend.engine.parametros;

import java.util.List;

/**
 * Familia SÓLO DE TEST con los 15 parámetros de riego de {@code reglas_v2 §11 / §6.3}
 * (design D2). Prueba que el catálogo alcanza para lo que necesita el cambio siguiente sin
 * publicar parámetros que ninguna regla usa todavía.
 */
enum ParametrosRiegoV2Fixture implements DefinicionParametro {

    UMBRAL_HUMEDAD("riego.umbral-humedad", TipoParametro.NUMERO, "%", "45", 35.0, 60.0, 0),
    HUMEDAD_OBJETIVO("riego.humedad-objetivo", TipoParametro.NUMERO, "%", "65", 55.0, 75.0, 0),
    UMBRAL_CRITICO("riego.umbral-critico", TipoParametro.NUMERO, "%", "35", 25.0, 40.0, 0),
    SATURACION_BLOQUEO("riego.saturacion-bloqueo", TipoParametro.NUMERO, "%", "75", 65.0, 85.0, 0),
    SATURACION_ALERTA("riego.saturacion-alerta", TipoParametro.NUMERO, "%", "80", 65.0, 85.0, 0),
    LITROS_POR_PUNTO("riego.litros-por-punto", TipoParametro.NUMERO, "L/punto", "0.2", 0.1, 0.5, 2),
    VOLUMEN_MAX_EVENTO("riego.volumen-max-evento", TipoParametro.NUMERO, "L", "6", 3.0, 10.0, 0),
    CAUDAL_EMISOR("riego.caudal-emisor", TipoParametro.NUMERO, "L/h", "30", null, null, 1),
    SECTORES_SIMULTANEOS("riego.sectores-simultaneos", TipoParametro.ENTERO, "sectores", "10", 1.0, 100.0, 0),
    VENTANA_NORMAL("riego.ventana-normal", TipoParametro.VENTANA_HORARIA, "", "06:00-18:00", null, null, 0),
    LLUVIA_PROBABILIDAD("riego.lluvia-probabilidad", TipoParametro.NUMERO, "%", "70", 50.0, 95.0, 0),
    LLUVIA_MM("riego.lluvia-mm", TipoParametro.NUMERO, "mm", "5", 2.0, 20.0, 0),
    LLUVIA_VENTANA("riego.lluvia-ventana", TipoParametro.NUMERO, "h", "4", 2.0, 12.0, 0),
    PAUSA_TRAS_APLICACION("riego.pausa-tras-aplicacion", TipoParametro.NUMERO, "h", "6", 2.0, 24.0, 0),
    EXCEPTUADO_BLOQUEO("riego.exceptuado-bloqueo", TipoParametro.NUMERO, "h", "12", 6.0, 24.0, 0);

    /** `crítico < umbral < objetivo` y `bloqueo ≤ alerta`: las que declara la familia de riego en v2. */
    static final List<RestriccionCruzada> RESTRICCIONES = List.of(
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
                    "El bloqueo por saturación no puede superar la alerta de saturación."));

    private final String clave;
    private final TipoParametro tipo;
    private final String unidad;
    private final String fabrica;
    private final Double min;
    private final Double max;
    private final int decimales;

    ParametrosRiegoV2Fixture(String clave, TipoParametro tipo, String unidad, String fabrica,
                             Double min, Double max, int decimales) {
        this.clave = clave;
        this.tipo = tipo;
        this.unidad = unidad;
        this.fabrica = fabrica;
        this.min = min;
        this.max = max;
        this.decimales = decimales;
    }

    @Override public String clave() { return clave; }
    @Override public String etiqueta() { return "Fixture " + clave; }
    @Override public String descripcion() { return "Parámetro de prueba " + clave; }
    @Override public FamiliaParametro familia() { return FamiliaParametro.RIEGO; }
    @Override public TipoParametro tipo() { return tipo; }
    @Override public String unidad() { return unidad; }
    @Override public String fabrica() { return fabrica; }
    @Override public Double min() { return min; }
    @Override public Double max() { return max; }
    @Override public int decimales() { return decimales; }
    @Override public String refSpec() { return "reglas_v2 §11 Riego"; }
}
