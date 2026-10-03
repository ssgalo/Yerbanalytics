package com.yerbanalytics.backend.mqtt;

/**
 * Contrato MQTT del riel de la cámara. Espejo de la sección "Riel" de
 * {@code Desarrollo/embebido/comun/contrato.h} (<strong>fuente de verdad</strong>); si algo cambia,
 * cambia allá primero. Los otros espejos son {@code simulador/server/contract.ts} y el sketch
 * {@code vivero_esp32_red}.
 *
 * <p>Un solo riel, sin id en el tópico. El backend publica el comando (QoS 1) y consume el evento.
 *
 * <p>Comando: {@code {"commandId","actuador":"rail","accion":"IR_A"|"HOME","parametros":{"posicion":1|2}}}.
 * Evento: {@code {"commandId","status","posicion","pasos"[,"codigo","detalle"]}}.
 */
public final class ContratoRiel {

    private ContratoRiel() {
    }

    /** backend → ESP32. */
    public static final String TOPIC_COMANDO = "nursery/rail/command";
    /** ESP32 → backend. */
    public static final String TOPIC_EVENTO = "nursery/rail/event";

    /** Valor de {@code actuador} del comando. */
    public static final String ACTUADOR = "rail";

    // accion
    public static final String ACCION_IR_A = "IR_A";
    public static final String ACCION_HOME = "HOME";

    // status del evento
    public static final String STATUS_ACEPTADO = "ACEPTADO";
    public static final String STATUS_LLEGO = "LLEGO";
    public static final String STATUS_ERROR = "ERROR";

    // codigo del evento ERROR
    public static final String CODIGO_COMANDO_INVALIDO = "COMANDO_INVALIDO";
    public static final String CODIGO_HOME_NO_ENCONTRADO = "HOME_NO_ENCONTRADO";
    public static final String CODIGO_FIN_DE_CARRERA = "FIN_DE_CARRERA";
    public static final String CODIGO_REEMPLAZADO = "REEMPLAZADO";
}
