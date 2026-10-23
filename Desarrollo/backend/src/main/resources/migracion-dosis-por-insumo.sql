-- ============================================================================
--  Migración manual · dosis por insumo (openspec/changes/add-dosificacion-base)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. El backend ARRANCA IGUAL sin correrlo: un
--  override de una clave que ya no existe se ignora con un warn.
--
--  QUÉ CAMBIA
--    · La dosis única `insumo.dosis-ml` se reparte en dos: `insumo.dosis-fertilizante`
--      e `insumo.dosis-fitosanitario`. La dosificación v1 sólo aplicaba un producto
--      genérico, que ahora es el fertilizante, así que el valor editado se copia
--      a esa clave. El fitosanitario arranca de fábrica.
--    · Se borra el override viejo.
--
--  Idempotente: si `insumo.dosis-ml` ya no está, no hace nada.
-- ============================================================================

INSERT INTO parametro_regla (clave, valor, updated_by, updated_ts)
SELECT 'insumo.dosis-fertilizante', valor, updated_by, updated_ts
FROM parametro_regla
WHERE clave = 'insumo.dosis-ml'
  AND NOT EXISTS (SELECT 1 FROM parametro_regla WHERE clave = 'insumo.dosis-fertilizante');

DELETE FROM parametro_regla WHERE clave = 'insumo.dosis-ml';
