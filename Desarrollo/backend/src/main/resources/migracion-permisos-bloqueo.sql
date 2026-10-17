-- ============================================================================
--  Migración manual · permisos del bloqueo manual (bloqueos.ver / bloqueos.gestionar)
--  (openspec/changes/add-bloqueo-manual, bloque 1)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. La matriz de permisos se siembra únicamente
--  cuando `rol_permiso` está vacía, así que una base anterior a este cambio no
--  tiene los dos permisos nuevos y nadie (salvo quien edite la matriz) podría
--  ver ni gestionar bloqueos. Este script agrega los de la matriz por defecto
--  (`Permiso.matrizPorDefecto`):
--
--    bloqueos.ver        Administrador, Ingeniero Agrónomo, Productor Viverista, Operario
--    bloqueos.gestionar  Administrador, Ingeniero Agrónomo, Operario
--
--  El rol Servicio no los recibe acá: el simulador los pide con su propia
--  matriz (ver el README del simulador).
--
--  QUÉ SE PIERDE
--    Nada. Sólo inserta filas que no existan; no toca las que el Administrador
--    editó. Si alguien quitó un permiso de la matriz a propósito, este script
--    NO sabe de eso para estos dos códigos (no existían), así que los agrega.
--
--  ORDEN DE EJECUCIÓN
--    Idempotente; se puede correr antes o después de arrancar la versión nueva.
--    Las sesiones abiertas leen la matriz al confirmarse la próxima edición o al
--    reiniciar el backend (la matriz se guarda en memoria).
--
--  Rollback: DELETE FROM rol_permiso WHERE permiso IN ('bloqueos.ver', 'bloqueos.gestionar');
-- ============================================================================

INSERT INTO rol_permiso (rol, permiso)
SELECT v.rol, v.permiso
FROM (VALUES
    ('ADMINISTRADOR',       'bloqueos.ver'),
    ('INGENIERO_AGRONOMO',  'bloqueos.ver'),
    ('PRODUCTOR_VIVERISTA', 'bloqueos.ver'),
    ('OPERARIO',            'bloqueos.ver'),
    ('ADMINISTRADOR',       'bloqueos.gestionar'),
    ('INGENIERO_AGRONOMO',  'bloqueos.gestionar'),
    ('OPERARIO',            'bloqueos.gestionar')
) AS v(rol, permiso)
WHERE NOT EXISTS (
    SELECT 1 FROM rol_permiso rp WHERE rp.rol = v.rol AND rp.permiso = v.permiso
);
