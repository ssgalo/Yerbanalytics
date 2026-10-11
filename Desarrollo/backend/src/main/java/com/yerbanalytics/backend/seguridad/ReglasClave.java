package com.yerbanalytics.backend.seguridad;

/** Reglas mínimas de una contraseña nueva: al menos 8 caracteres y distinta del nombre de usuario. */
public final class ReglasClave {

    public static final int LONGITUD_MINIMA = 8;

    private ReglasClave() {
    }

    public static void validar(String username, String clave) {
        if (clave == null || clave.length() < LONGITUD_MINIMA) {
            throw new SeguridadExceptions.Invalida(
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }
        if (username != null && clave.equalsIgnoreCase(username)) {
            throw new SeguridadExceptions.Invalida("La contraseña no puede ser igual al nombre de usuario.");
        }
    }
}
