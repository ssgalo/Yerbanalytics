package com.yerbanalytics.backend.seguridad;

/**
 * Los cinco roles del sistema. Son fijos: no se crean, renombran ni borran. Lo que el
 * Administrador edita es qué permisos tiene cada uno (tabla {@code rol_permiso}).
 *
 * <p>{@link #SERVICIO} es para integraciones (p. ej. el servicio de inferencia), no para
 * personas: el dashboard lo presenta así, pero el backend lo trata como un rol más.
 */
public enum Rol {

    ADMINISTRADOR("Administrador"),
    INGENIERO_AGRONOMO("Ingeniero Agrónomo"),
    PRODUCTOR_VIVERISTA("Productor Viverista"),
    OPERARIO("Operario"),
    SERVICIO("Servicio");

    private final String nombre;

    Rol(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }
}
