package com.yerbanalytics.backend.mqtt;

/**
 * Claves y unidades del contrato MQTT del nodo ESP32.
 *
 * <p><b>Fuente de verdad:</b> {@code Desarrollo/embebido/comun/contrato.h}. Este archivo es
 * su espejo del lado backend — si el firmware cambia una clave o una unidad, se actualiza
 * acá y no en cada consumidor suelto.
 *
 * <p><b>Invariante:</b> todo {@code MqttTelemetryPayload} viaja en unidades del contrato.
 * La ingesta es el único punto que convierte a las unidades canónicas de la plataforma, y
 * quien publica (simulador automático o envío manual del dashboard) traduce al salir. Así
 * no hay payloads en unidades mixtas dando vueltas.
 *
 * <p><b>Unidades tal como las publica el nodo:</b>
 * <ul>
 *   <li>{@code humSus} %RH · {@code humAmb} %RH · {@code temp} °C aire · {@code tempSuelo} °C</li>
 *   <li>{@code ce} <b>µS/cm</b> — la plataforma persiste dS/m.</li>
 *   <li>{@code uv} <b>% de luz de un LDR</b>, no índice UV. La clave se conserva por
 *       compatibilidad con el firmware ya escrito; la métrica es luminosidad.</li>
 *   <li>{@code phSuelo} pH · {@code n}/{@code p}/{@code k} mg/kg</li>
 *   <li>{@code salinidad} y {@code tds} mg/L — la sonda los deriva por factor de la misma
 *       medición de EC, así que no se modelan. Se aceptan y se descartan.</li>
 * </ul>
 */
public final class ContratoNodo {

    /** 1 dS/m = 1000 µS/cm. */
    public static final double CE_USCM_POR_DSM = 1000.0;

    private ContratoNodo() {
    }

    /** Conductividad del contrato (µS/cm) a la unidad canónica de la plataforma (dS/m). */
    public static Double ceADsPorM(Double ceMicroSPorCm) {
        return ceMicroSPorCm == null ? null : ceMicroSPorCm / CE_USCM_POR_DSM;
    }

    /** Conductividad en dS/m a la unidad del contrato (µS/cm), para publicar. */
    public static Double ceAMicroSPorCm(Double ceDsPorM) {
        return ceDsPorM == null ? null : ceDsPorM * CE_USCM_POR_DSM;
    }
}
