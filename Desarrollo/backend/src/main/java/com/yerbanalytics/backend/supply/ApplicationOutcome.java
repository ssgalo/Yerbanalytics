package com.yerbanalytics.backend.supply;

/**
 * Qué pasó con el pedido de una aplicación.
 *
 * @param status {@code APPLIED}: la orden salió al nodo · {@code DEFERRED}: no es el momento y queda pendiente para la
 *               próxima ventana apta · {@code REJECTED}: no corresponde y no se reintenta
 * @param reason el motivo del diferimiento o del rechazo; {@code null} si se aplicó
 */
public record ApplicationOutcome(Status status, String reason) {

    public enum Status {
        APPLIED, DEFERRED, REJECTED
    }

    public static ApplicationOutcome applied() {
        return new ApplicationOutcome(Status.APPLIED, null);
    }

    public static ApplicationOutcome deferred(String reason) {
        return new ApplicationOutcome(Status.DEFERRED, reason);
    }

    public static ApplicationOutcome rejected(String reason) {
        return new ApplicationOutcome(Status.REJECTED, reason);
    }
}
