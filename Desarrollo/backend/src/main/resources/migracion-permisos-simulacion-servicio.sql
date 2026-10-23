-- ============================================================================
--  Migración manual · permiso de simulación para el rol Servicio (simulacion.gestionar)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. La matriz de permisos se siembra únicamente
--  cuando `rol_permiso` está vacía, así que una base sembrada antes de que el rol
--  Servicio recibiera `simulacion.gestionar` en `Permiso.matrizPorDefecto` no lo
--  tiene, y el simulador recibe 403 en `/api/demo/**` (reglas, cooldowns, fuente de
--  telemetría): «No tenés permiso para esta acción».
--
--  QUÉ SE PIERDE
--    Nada. Sólo inserta la fila si no existe; no toca las que el Administrador editó.
--
--  ORDEN DE EJECUCIÓN
--    Idempotente. La matriz se guarda en memoria: rige al confirmarse la próxima
--    edición o al reiniciar el backend.
--
--  Rollback: DELETE FROM rol_permiso WHERE rol = 'SERVICIO' AND permiso = 'simulacion.gestionar';
-- ============================================================================

INSERT INTO rol_permiso (rol, permiso)
SELECT v.rol, v.permiso
FROM (VALUES
    ('SERVICIO', 'simulacion.gestionar')
) AS v(rol, permiso)
WHERE NOT EXISTS (
    SELECT 1 FROM rol_permiso rp WHERE rp.rol = v.rol AND rp.permiso = v.permiso
);
