package com.yerbanalytics.backend.engine.parametros;

/**
 * Un pedido sobre una pieza que su estado no admite (prender una eliminada, eliminar una protección). El controller lo
 * traduce a un {@code 409} con {@code error} = {@link #codigo()}.
 */
public class ReglaConflictoException extends RuntimeException {

    private final String codigo;

    public ReglaConflictoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
