-- ============================================================================
--  Migración manual · permiso para registrar la revisión de una acción sin efectividad
--  (efectividad.revisar) (openspec/changes/add-efectividad-y-s06, bloque 5.1)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. La matriz de permisos se siembra únicamente
--  cuando `rol_permiso` está vacía, así que una base anterior a este cambio no
--  tiene `efectividad.revisar`. Este script agrega las filas de la matriz por
--  defecto (`Permiso.matrizPorDefecto`):
--
--    efectividad.revisar    Ingeniero Agrónomo · Operario
--
--  Ambos roles ya tienen `historial.ver`, el par de lectura que exige el permiso (la
--  revisión se registra desde el evento del historial). El rol Servicio NO lo recibe:
--  el simulador levanta el bloqueo con el bypass `block` de la regla, no revisando.
--
--  El Administrador ya tiene todo el catálogo desde que se edita su matriz desde
--  el dashboard; si no, agregale `efectividad.revisar` desde Configuración → Usuarios.
--
--  Quién lo necesita: quien constata en persona qué le pasó al riego o al cultivo
--  (el Operario y el Ingeniero están en el vivero) y levanta el bloqueo S-06.
--
--  QUÉ SE PIERDE
--    Nada. Sólo inserta las filas si no existen; no toca las que el Administrador editó.
--
--  ORDEN DE EJECUCIÓN
--    Idempotente; se puede correr antes o después de arrancar la versión nueva.
--    Las columnas nuevas de `historial_evento` (`efectividad_tipo`, `diag_estado_antes`,
--    `vecinos_antes`, `evo_motivo`) las agrega Hibernate (ddl-auto=update). Los
--    seguimientos que estaban abiertos al actualizar los reubica el servicio de efectividad
--    en su primer ciclo (los de riego se evalúan como riego; los demás se cierran sin veredicto).
--    La matriz se guarda en memoria: rige al confirmarse la próxima edición o al
--    reiniciar el backend.
--
--  Rollback:
--    DELETE FROM rol_permiso WHERE permiso = 'efectividad.revisar';
-- ============================================================================

INSERT INTO rol_permiso (rol, permiso)
SELECT v.rol, v.permiso
FROM (VALUES
    ('INGENIERO_AGRONOMO', 'efectividad.revisar'),
    ('OPERARIO',           'efectividad.revisar')
) AS v(rol, permiso)
WHERE NOT EXISTS (
    SELECT 1 FROM rol_permiso rp WHERE rp.rol = v.rol AND rp.permiso = v.permiso
);
