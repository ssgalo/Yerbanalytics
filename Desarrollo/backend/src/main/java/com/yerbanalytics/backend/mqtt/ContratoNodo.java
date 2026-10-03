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
