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
 * quien publica (el firmware del nodo, o el simulador que lo reemplaza) traduce al salir. Así
 * no hay payloads en unidades mixtas dando vueltas.
 *
 * <p><b>Unidades tal como las publica el nodo:</b>
 * <ul>
 *   <li>{@code humSus} %RH · {@code humAmb} %RH · {@code temp} °C aire · {@code tempSuelo} °C</li>
 *   <li>{@code ce} <b>µS/cm</b> — la plataforma persiste dS/m.</li>
 *   <li>{@code uv} <b>% de luz de un LDR</b>, no índice UV. La clave se conserva por
 *       compatibilidad con el firmware ya escrito; la métrica es luminosidad.</li>
 *   <li>{@code phSuelo} pH · {@code n}/{@code p}/{@code k} mg/kg</li>
 *   <li>{@code timestamp} <b>segundos epoch</b> en el firmware, milisegundos en el simulador:
 *       la ingesta lo normaliza a ms por valor (ver {@link #timestampAMs}).</li>
 *   <li>{@code salinidad} y {@code tds} mg/L — la sonda los deriva por factor de la misma
 *       medición de EC, así que no se modelan. Se aceptan y se descartan.</li>
 * </ul>
 */
public final class ContratoNodo {

    /** 1 dS/m = 1000 µS/cm. */
    public static final double CE_USCM_POR_DSM = 1000.0;

    /** 2020-01-01T00:00:00Z en ms: nada anterior es una fecha real de este sistema. */
    private static final long EPOCH_MINIMO_MS = 1_577_836_800_000L;

    /**
     * Por debajo de esto un epoch está en segundos, por encima en milisegundos: 1e11 s es el año
     * 5138, 1e11 ms es 1973, y ninguno de los dos rangos reales se solapa con el otro.
     */
    private static final long CORTE_SEGUNDOS_MS = 100_000_000_000L;

    /** Deriva tolerada hacia el futuro del reloj del nodo. */
    private static final long TOLERANCIA_FUTURO_MS = 5 * 60_000L;

    /**
     * Duración máxima, en segundos, que el backend puede pedir en {@code durationSec} de un comando
     * {@code valve ON}. Espeja {@code CONTRATO_VALVULA_DURACION_MAX_SEG} de {@code contrato.h}: cubre
     * todo el rango del volumen máximo de riego (3–10 L) al caudal nominal (10 L / 30 L/h = 1200 s).
     */
    public static final int DURACION_VALVULA_MAX_SEG = 1200;

    /**
     * Comando de zona (backend → nodo testigo), con el id de la zona. Espeja la sección "Comando de
     * zona" de {@code contrato.h} ({@code contratoTopicComandoZona}). QoS 1, sin retain. A nivel zona
     * y no sector porque el sensado es por macro-zona.
     */
    public static final String TOPIC_COMANDO_ZONA = "nursery/zone/%s/command";

    /**
     * {@code accion} del comando de zona: el nodo lee sus sensores y publica la telemetría de siempre
     * (sin {@code commandId}, sin ACK). Espeja {@code ACCION_LEER_AHORA} de {@code contrato.h}.
     */
    public static final String ACCION_LEER_AHORA = "LEER_AHORA";

    /**
     * Suscripción a los acks de todos los sectores. El nodo publica el resultado de cada comando en
     * {@code nursery/zone/{zona}/sector/{sector}/ack} (espeja {@code contratoTopicAck} de {@code contrato.h}).
     */
    public static final String TOPIC_ACK_SUSCRIPCION = "nursery/zone/+/sector/+/ack";

    /**
     * Errores del nodo (S-05): el nodo detecta una falla, corta el actuador y la publica UNA vez en el tópico de su
     * zona. Con el id de la zona. Espeja {@code contratoTopicErrores} de {@code contrato.h}. QoS 1, sin retain.
     * Los tipos están en {@link NodeErrorType}.
     */
    public static final String TOPIC_ERRORS = "nursery/zone/%s/errores";

    /** Suscripción a los errores de todas las zonas. */
    public static final String TOPIC_ERRORS_SUBSCRIPTION = "nursery/zone/+/errores";

    /** {@code status} de un ack: la acción terminó bien. */
    public static final String ACK_SUCCESS = "SUCCESS";
    /** {@code status} de un ack: la acción no se pudo hacer o no terminó; {@code detalle.tipo} dice por qué. */
    public static final String ACK_ERROR = "ERROR";

    /** {@code detalle.tipo} de un ERROR que no es una falla: llegó otro comando para lo mismo antes de terminar. */
    public static final String ACK_TIPO_REEMPLAZADO = "reemplazado";
    /** {@code detalle.tipo} de un ERROR que no es una falla: llegó un OFF. */
    public static final String ACK_TIPO_CANCELADO = "cancelado";
    /** {@code detalle.tipo}: la mediasombra no llegó al final de carrera a tiempo. */
    public static final String ACK_TIPO_FALLA_MECANICA = "falla_mecanica";

    private ContratoNodo() {
    }

    /**
     * Normaliza el {@code timestamp} del payload a milisegundos epoch, la unidad con la que la
     * plataforma guarda y compara las lecturas.
     *
     * <p>El contrato no fija la unidad: el firmware publica <b>segundos</b> epoch
     * ({@code reloj::ahoraEpoch()}) y el simulador <b>milisegundos</b>. Se decide por el valor,
     * no por quién publica: segundos plausibles se multiplican por 1000 y milisegundos plausibles
     * se dejan. Devuelve {@code null} si el valor no sirve como fecha real —ausente, anterior a
     * 2020 (el nodo sin NTP manda segundos desde el arranque) o adelantado más de unos minutos—
     * y entonces el llamador usa la hora de recepción.
     *
     * @param ts      el {@code timestamp} tal como llegó (puede ser {@code null})
     * @param ahoraMs la hora de recepción, en ms epoch
     */
    public static Long timestampAMs(Long ts, long ahoraMs) {
        if (ts == null || ts <= 0) {
            return null;
        }
        long ms = ts < CORTE_SEGUNDOS_MS ? ts * 1000 : ts;
        if (ms < EPOCH_MINIMO_MS || ms > ahoraMs + TOLERANCIA_FUTURO_MS) {
            return null;
        }
        return ms;
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
