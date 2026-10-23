package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.model.DiagnosticoEntity;

import java.time.Duration;
import java.time.Instant;

/**
 * P-D1 (reglas_v2 §1.3): cuándo un diagnóstico de IA todavía alcanza para aplicar un insumo. Lógica pura: no sabe de
 * repositorios ni de reglas. Una regla que dispara con el diagnóstico (F-*) y el {@link ApplicationService} usan lo mismo.
 *
 * <p>Vigente = es el último del sector, tiene a lo sumo {@code maxAgeHours}, no es "No concluyente" y su confianza
 * alcanza el umbral.
 */
public final class ValidDiagnosis {

    static final String INCONCLUSIVE = "No concluyente";
    private static final String NO_DIAGNOSIS = "el sector no tiene diagnóstico";

    private ValidDiagnosis() {
    }

    /**
     * @param valid  si el diagnóstico sirve para aplicar
     * @param reason por qué no sirve; {@code null} si sirve
     */
    public record Verdict(boolean valid, String reason) {

        static Verdict ok() {
            return new Verdict(true, null);
        }

        static Verdict invalid(String reason) {
            return new Verdict(false, reason);
        }
    }

    /**
     * @param latest           el diagnóstico más reciente del sector, o {@code null} si nunca tuvo
     * @param now              instante actual del vivero
     * @param minConfidencePct umbral de confianza ({@code diagnostico.confianza-minima})
     * @param maxAgeHours      vigencia ({@code diagnostico.vigencia-horas})
     */
    public static Verdict of(DiagnosticoEntity latest, Instant now, double minConfidencePct, double maxAgeHours) {
        if (latest == null) {
            return Verdict.invalid(NO_DIAGNOSIS);
        }
        return of(latest.getEstado(), latest.getConf(), latest.getCreadoEn(), now, minConfidencePct, maxAgeHours);
    }

    /**
     * Lo mismo con los datos del último diagnóstico tal como los guarda el sector (estado, confianza y llegada): así una
     * regla que ya tiene el sector en el contexto no consulta la tabla de diagnósticos.
     *
     * @param state       estado del diagnóstico
     * @param confidence  confianza en %, o {@code null} si no tiene
     * @param createdAt   cuándo llegó (epoch ms), o {@code null} si el sector nunca recibió uno
     * @param maxAgeHours cuánto tiempo sigue valiendo: la vigencia de P-D1 o, para quien mira una ventana propia (F-04), esa ventana
     */
    public static Verdict of(String state, Double confidence, Long createdAt, Instant now, double minConfidencePct,
                             double maxAgeHours) {
        if (createdAt == null || state == null) {
            return Verdict.invalid(NO_DIAGNOSIS);
        }
        if (INCONCLUSIVE.equals(state)) {
            return Verdict.invalid("el último diagnóstico es No concluyente");
        }
        double ageHours = Duration.between(Instant.ofEpochMilli(createdAt), now).toMillis() / 3_600_000.0;
        if (ageHours > maxAgeHours) {
            return Verdict.invalid(String.format("el diagnóstico tiene %.0f h (vigencia %.0f h)", ageHours, maxAgeHours));
        }
        if (confidence == null || confidence < minConfidencePct) {
            return Verdict.invalid(String.format("la confianza del diagnóstico (%s) no alcanza el %.0f %% requerido",
                    confidence == null ? "sin dato" : String.format("%.0f %%", confidence), minConfidencePct));
        }
        return Verdict.ok();
    }
}
