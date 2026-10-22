-- ============================================================================
--  Migración manual · permisos de los tanques de insumo (insumos.ver / insumos.gestionar)
--  (openspec/changes/add-dosificacion-base)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. La matriz de permisos se siembra únicamente
--  cuando `rol_permiso` está vacía, así que una base anterior a este cambio no
--  tiene los dos permisos nuevos y nadie (salvo quien edite la matriz) podría ver
--  los tanques ni registrar una carga. Este script agrega los de la matriz por
--  defecto (`Permiso.matrizPorDefecto`):
--
--    insumos.ver        Administrador, Ingeniero Agrónomo, Productor Viverista, Operario, Servicio
--    insumos.gestionar  Administrador, Ingeniero Agrónomo, Operario, Servicio
--
--  El Operario es quien repone el producto, así que es quien registra la carga.
--  El rol Servicio los recibe porque el simulador (cuenta de rol Servicio) carga
--  los tanques desde su escenario de precondiciones de aplicación. Si preferís
--  separarlo, quitá esas dos filas desde la matriz del dashboard
--  (Configuración → Usuarios).
--
--  QUÉ SE PIERDE
--    Nada. Sólo inserta filas que no existan; no toca las que el Administrador editó.
--
--  ORDEN DE EJECUCIÓN
--    Idempotente; se puede correr antes o después de arrancar la versión nueva.
--    La matriz se guarda en memoria: reiniciá el backend o editá la matriz desde
--    el dashboard para que las sesiones la lean.
--
--  Rollback: DELETE FROM rol_permiso WHERE permiso IN ('insumos.ver', 'insumos.gestionar');
-- ============================================================================

INSERT INTO rol_permiso (rol, permiso)
SELECT v.rol, v.permiso
FROM (VALUES
    ('ADMINISTRADOR',       'insumos.ver'),
    ('INGENIERO_AGRONOMO',  'insumos.ver'),
    ('PRODUCTOR_VIVERISTA', 'insumos.ver'),
    ('OPERARIO',            'insumos.ver'),
    ('SERVICIO',            'insumos.ver'),
    ('ADMINISTRADOR',       'insumos.gestionar'),
    ('INGENIERO_AGRONOMO',  'insumos.gestionar'),
    ('OPERARIO',            'insumos.gestionar'),
    ('SERVICIO',            'insumos.gestionar')
) AS v(rol, permiso)
WHERE NOT EXISTS (
    SELECT 1 FROM rol_permiso rp WHERE rp.rol = v.rol AND rp.permiso = v.permiso
);
