package com.yerbanalytics.backend.supply;

/**
 * Resultado de evaluar las precondiciones de una aplicación, con el motivo para la traza.
 *
 * @param verdict qué hacer con la aplicación
 * @param reason  por qué; {@code null} si se cumplen
 */
public record PreconditionResult(Verdict verdict, String reason) {

    /** {@code MET}: se puede aplicar · {@code DEFER}: espera la próxima ventana apta · {@code REJECT}: no corresponde. */
    public enum Verdict {
        MET, DEFER, REJECT
    }

    private static final PreconditionResult MET = new PreconditionResult(Verdict.MET, null);

    public static PreconditionResult met() {
        return MET;
    }

    public static PreconditionResult defer(String reason) {
        return new PreconditionResult(Verdict.DEFER, reason);
    }

    public static PreconditionResult reject(String reason) {
        return new PreconditionResult(Verdict.REJECT, reason);
    }

    public boolean isMet() {
        return verdict == Verdict.MET;
    }
}
