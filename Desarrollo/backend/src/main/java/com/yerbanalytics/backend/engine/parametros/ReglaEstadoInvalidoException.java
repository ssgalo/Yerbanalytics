package com.yerbanalytics.backend.engine.parametros;

/**
 * Un pedido de cambio de estado de una regla que no cumple lo que se exige. {@code codigo} es lo que la API devuelve en
 * {@code error}; {@code consecuencia} sólo viaja con {@code CONFIRMACION_REQUERIDA}.
 */
public class ReglaEstadoInvalidoException extends RuntimeException {

    private final String codigo;
    private final String consecuencia;

    public ReglaEstadoInvalidoException(String codigo, String mensaje, String consecuencia) {
        super(mensaje);
        this.codigo = codigo;
        this.consecuencia = consecuencia;
    }

    public String codigo() {
        return codigo;
    }

    /** El texto de lo que pasa al apagar la protección; {@code null} en los demás errores. */
    public String consecuencia() {
        return consecuencia;
    }
}
