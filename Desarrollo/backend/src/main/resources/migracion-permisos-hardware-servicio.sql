-- ============================================================================
--  Migración manual · permiso de lectura del hardware para el rol Servicio (hardware.ver)
--  (openspec/changes/add-seguridad-lectura-hardware, bloque 5)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. La matriz de permisos se siembra únicamente
--  cuando `rol_permiso` está vacía, así que una base anterior a este cambio no
--  le da `hardware.ver` al rol Servicio. Este script agrega esa fila de la matriz
--  por defecto (`Permiso.matrizPorDefecto`):
--
--    hardware.ver        Servicio   (el resto de los roles ya lo tenían o no lo necesita)
--
--  El rol Servicio lo recibe porque el simulador (cuenta de rol Servicio) consulta qué
--  sectores ya no tienen electroválvula registrada para armar su escenario «Hardware
--  incompleto» (S-04). Es SÓLO LECTURA: `hardware.gestionar` (alta y recambio) no se
--  concede. Es el mismo rol que usa el servicio de inferencia: si preferís separarlos,
--  quitá esta fila desde la matriz del dashboard (Configuración → Usuarios) y el
--  escenario avisará que el backend lo rechaza.
--
--  QUÉ SE PIERDE
--    Nada. Sólo inserta la fila si no existe; no toca las que el Administrador editó.
--
--  ORDEN DE EJECUCIÓN
--    Idempotente; se puede correr antes o después de arrancar la versión nueva.
--    La matriz se guarda en memoria: rige al confirmarse la próxima edición o al
--    reiniciar el backend.
--
--  Rollback: DELETE FROM rol_permiso WHERE rol = 'SERVICIO' AND permiso = 'hardware.ver';
-- ============================================================================

INSERT INTO rol_permiso (rol, permiso)
SELECT v.rol, v.permiso
FROM (VALUES
    ('SERVICIO', 'hardware.ver')
) AS v(rol, permiso)
WHERE NOT EXISTS (
    SELECT 1 FROM rol_permiso rp WHERE rp.rol = v.rol AND rp.permiso = v.permiso
);
