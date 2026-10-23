package com.yerbanalytics.backend.supply;

/**
 * Resultado de evaluar las precondiciones de una aplicación, con el motivo para la traza.
 *
 * @param verdict qué hacer con la aplicación
 * @param reason  por qué; {@code null} si se cumplen
 * @param capReached {@code true} si se rechaza porque el sector alcanzó el tope de fitosanitarios (F-06): además de no
 *                   aplicarse, hay que avisar al operador y esperar una revisión
 */
public record PreconditionResult(Verdict verdict, String reason, boolean capReached) {

    public PreconditionResult(Verdict verdict, String reason) {
        this(verdict, reason, false);
    }

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

    /** F-06: el sector alcanzó el tope de fitosanitarios y no se le aplica otro hasta que alguien registre la revisión. */
    public static PreconditionResult rejectCap(String reason) {
        return new PreconditionResult(Verdict.REJECT, reason, true);
    }

    public boolean isMet() {
        return verdict == Verdict.MET;
    }
}
