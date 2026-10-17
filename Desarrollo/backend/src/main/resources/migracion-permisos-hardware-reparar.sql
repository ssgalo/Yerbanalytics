-- ============================================================================
--  Migración manual · permiso para registrar reparaciones de hardware (hardware.reparar)
--  (openspec/changes/add-fallas-esp32, bloque 2.6)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. La matriz de permisos se siembra únicamente
--  cuando `rol_permiso` está vacía, así que una base anterior a este cambio no
--  tiene `hardware.reparar`. Este script agrega las filas de la matriz por
--  defecto (`Permiso.matrizPorDefecto`):
--
--    hardware.reparar    Ingeniero Agrónomo · Operario · Servicio
--    hardware.ver        Operario   (la reparación exige poder ver el panel de hardware)
--
--  El Administrador ya tiene todo el catálogo desde que se edita su matriz desde
--  el dashboard; si no, agregale `hardware.reparar` desde Configuración → Usuarios.
--  `hardware.gestionar` (alta y recambio de dispositivos) NO se concede a nadie más.
--
--  Quién lo necesita: el Operario y el Ingeniero son quienes están en el vivero
--  cuando el nodo informa una falla; el rol Servicio lo usa el simulador para su
--  escenario «Reparación» (S-05).
--
--  QUÉ SE PIERDE
--    Nada. Sólo inserta las filas si no existen; no toca las que el Administrador editó.
--
--  ORDEN DE EJECUCIÓN
--    Idempotente; se puede correr antes o después de arrancar la versión nueva.
--    La columna nueva `dispositivo.falla_desde` la agrega Hibernate (ddl-auto=update).
--    La matriz se guarda en memoria: rige al confirmarse la próxima edición o al
--    reiniciar el backend.
--
--  Rollback:
--    DELETE FROM rol_permiso WHERE permiso = 'hardware.reparar';
--    (el `hardware.ver` del Operario se quita a mano si no se quiere conservar)
-- ============================================================================

INSERT INTO rol_permiso (rol, permiso)
SELECT v.rol, v.permiso
FROM (VALUES
    ('INGENIERO_AGRONOMO', 'hardware.reparar'),
    ('OPERARIO',           'hardware.reparar'),
    ('OPERARIO',           'hardware.ver'),
    ('SERVICIO',           'hardware.reparar')
) AS v(rol, permiso)
WHERE NOT EXISTS (
    SELECT 1 FROM rol_permiso rp WHERE rp.rol = v.rol AND rp.permiso = v.permiso
);
