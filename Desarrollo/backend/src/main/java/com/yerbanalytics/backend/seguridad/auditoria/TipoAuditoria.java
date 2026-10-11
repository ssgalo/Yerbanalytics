package com.yerbanalytics.backend.seguridad.auditoria;

/** Qué cambió. Cubre lo que pide HU-20 CA-03: usuarios, matriz de permisos y política de sesión. */
public enum TipoAuditoria {
    USUARIO_ALTA,
    USUARIO_EDITADO,
    USUARIO_ROL_CAMBIADO,
    USUARIO_SUSPENDIDO,
    USUARIO_REACTIVADO,
    USUARIO_BAJA,
    USUARIO_CLAVE_BLANQUEADA,
    ROL_PERMISOS_CAMBIADOS,
    POLITICA_SESION_CAMBIADA,
    ADMIN_INICIAL_CREADO,
    MATRIZ_SEMBRADA
}
