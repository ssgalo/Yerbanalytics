package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Piezas que comparten las reglas de riego: la humedad de la lectura, la condición "aplica R-01" y los
 * formatos de los motivos. Package-private: no es API del motor.
 *
 * <p>Las compuertas R-03, R-05 y R-06 sólo actúan cuando aplica R-01, o sea con
 * {@code crítico ≤ humedad < umbral de riego}. Fuera de ese rango no hay "riego por déficit común" que
 * posponer, cortar o pausar: con déficit crítico decide R-02 (que gana a las tres) y sin déficit no hay
 * nada que decidir. Así la traza no dice "pospuesto por lluvia" mientras R-02 riega, y nunca sale una
 * segunda válvula de R-01.
 */
final class RiegoRuleSupport {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private RiegoRuleSupport() {
    }

    /** Humedad de sustrato de la lectura, o {@code null} si no vino (sin lectura nunca se riega). */
    static Double humedad(RuleContext ctx) {
        return ctx.metricRaw("humSus");
    }

    /**
     * {@code true} si aplica R-01: {@code umbral-crítico ≤ humedad < umbral-humedad}. Registra las DOS
     * comparaciones (siempre, para que el Inspector muestre por qué la regla aplicó o no), así que la
     * regla que la llame debe declarar {@code UMBRAL_CRITICO} y {@code UMBRAL_HUMEDAD}.
     */
    static boolean aplicaR01(Evaluacion ev, Double humedad) {
        boolean sobreElCritico = ev.comparar("Humedad de sustrato", humedad, Operador.GE, ParametrosRiego.UMBRAL_CRITICO);
        boolean bajoElUmbral = ev.comparar("Humedad de sustrato", humedad, Operador.LT, ParametrosRiego.UMBRAL_HUMEDAD);
        return sobreElCritico && bajoElUmbral;
    }

    /** Motivo común de las compuertas cuando R-01 no aplica. */
    static String noAplica(Double humedad, double critico, double umbral) {
        if (humedad == null || humedad.isNaN()) {
            return "No aplica: sin lectura de humedad de sustrato.";
        }
        if (humedad < critico) {
            return String.format(ES_AR, "No aplica: la humedad %s%% está bajo el umbral crítico (%s%%), lo cubre el déficit crítico (R-02).",
                    num(humedad), num(critico));
        }
        return String.format(ES_AR, "No aplica: la humedad %s%% no está bajo el umbral de riego (%s%%), no hay déficit.",
                num(humedad), num(umbral));
    }

    static LocalDateTime fechaHoraLocal(RuleContext ctx) {
        return ctx.now().atZone(ZonaHorariaVivero.ZONA).toLocalDateTime();
    }

    static LocalTime horaLocal(RuleContext ctx) {
        return ctx.now().atZone(ZonaHorariaVivero.ZONA).toLocalTime();
    }

    /** "HH:mm" en la hora local del vivero. */
    static String hora(Instant instante) {
        return instante.atZone(ZonaHorariaVivero.ZONA).format(HH_MM);
    }

    static String hora(long epochMs) {
        return hora(Instant.ofEpochMilli(epochMs));
    }

    /** Horas (con decimales) entre un instante en epoch ms y {@code ahora}. */
    static double horasDesde(long epochMs, Instant ahora) {
        return (ahora.toEpochMilli() - epochMs) / 3_600_000.0;
    }

    /** Número sin ceros de más: 44 → "44", 4.2 → "4,2", 4.02 → "4,02". */
    static String num(double v) {
        return new java.math.BigDecimal(Double.toString(v)).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /** "4,2 L (504 s)". */
    static String volumenYTiempo(double litros, int segundos) {
        return num(litros) + " L (" + segundos + " s)";
    }
}
