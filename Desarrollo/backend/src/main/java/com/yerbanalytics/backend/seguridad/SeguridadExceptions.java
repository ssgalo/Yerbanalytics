package com.yerbanalytics.backend.seguridad;

/**
 * Errores de la gestión de seguridad, uno por código HTTP. Los controllers los traducen a
 * {@code {"error": mensaje}}; el mensaje se escribe para que lo lea el Administrador.
 */
public final class SeguridadExceptions {

    private SeguridadExceptions() {
    }

    /** {@code 400}: el pedido está mal formado o viola una regla de validación. */
    public static class Invalida extends RuntimeException {
        public Invalida(String mensaje) {
            super(mensaje);
        }
    }

    /** {@code 409}: el pedido es válido pero choca con el estado (salvaguardas, duplicados). */
    public static class Conflicto extends RuntimeException {
        public Conflicto(String mensaje) {
            super(mensaje);
        }
    }

    /** {@code 404}. */
    public static class NoEncontrado extends RuntimeException {
        public NoEncontrado(String mensaje) {
            super(mensaje);
        }
    }
}
