package com.yerbanalytics.backend.seguridad;

/** Por qué se cerró una sesión. Decide el {@code motivo} del {@code 401} siguiente. */
public enum MotivoCierre {
    /** El usuario cerró sesión. */
    LOGOUT,
    /** Venció el tiempo máximo de inactividad. */
    EXPIRADA,
    /** Cambió el rol, el estado, la contraseña o la matriz del rol del usuario. */
    REVOCADA
}
