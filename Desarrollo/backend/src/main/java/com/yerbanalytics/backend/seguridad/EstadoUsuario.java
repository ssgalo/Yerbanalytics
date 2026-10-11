package com.yerbanalytics.backend.seguridad;

/**
 * {@link #SUSPENDIDO} es reversible; {@link #BAJA} es definitiva y lógica: la fila se conserva
 * para la auditoría y el nombre de usuario no se reutiliza.
 */
public enum EstadoUsuario {
    ACTIVO,
    SUSPENDIDO,
    BAJA
}
