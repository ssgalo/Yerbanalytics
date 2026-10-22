-- ============================================================================
--  Migración manual · permisos de reglas y pasadas para el rol Servicio
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. La matriz de permisos se siembra únicamente
--  cuando `rol_permiso` está vacía, así que una base anterior no le da al rol
--  Servicio estas filas de la matriz por defecto (`Permiso.matrizPorDefecto`):
--
--    reglas.ver       GET /api/rules/** (catálogo de parámetros, traza de evaluación)
--    pasadas.ver      GET /api/pasadas/**, /api/secuencias/**
--    pasadas.operar   POST /api/pulsos, /api/secuencias, /api/pasadas
--
--  Las cuentas de integración (rol Servicio) leen el catálogo y la traza del motor y piden
--  pulsos, secuencias y pasadas. Sin estas filas el backend responde 403.
--  NO se concede ningún permiso de edición (`reglas.editar`, `configuracion.editar`).
--
--  QUÉ SE PIERDE
--    Nada. Sólo inserta las filas que faltan; no toca las que el Administrador editó.
--
--  ORDEN DE EJECUCIÓN
--    Idempotente; se puede correr antes o después de arrancar la versión nueva.
--    La matriz se guarda en memoria: rige al reiniciar el backend.
--
--  Rollback: DELETE FROM rol_permiso WHERE rol = 'SERVICIO'
--            AND permiso IN ('reglas.ver', 'pasadas.ver', 'pasadas.operar');
-- ============================================================================

INSERT INTO rol_permiso (rol, permiso)
SELECT v.rol, v.permiso
FROM (VALUES
    ('SERVICIO', 'reglas.ver'),
    ('SERVICIO', 'pasadas.ver'),
    ('SERVICIO', 'pasadas.operar')
) AS v(rol, permiso)
WHERE NOT EXISTS (
    SELECT 1 FROM rol_permiso rp WHERE rp.rol = v.rol AND rp.permiso = v.permiso
);
