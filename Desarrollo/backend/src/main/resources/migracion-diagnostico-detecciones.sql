-- ============================================================================
--  Migración manual · diagnóstico por detecciones
--  (openspec/changes/add-diagnostico-por-detecciones)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. `ddl-auto=update` crea la tabla nueva
--  `diagnosis_criteria` y agrega las columnas nuevas (`diagnostico.confidence`,
--  `diagnostico.detections`, `diagnostico.detection_count`,
--  `sector.diagnosis_confidence`), todas nullable. Lo que NO hace es quitar un
--  `NOT NULL` de una columna existente.
--
--  QUÉ CAMBIA Y POR QUÉ
--    · `diagnostico.conf` pasa a admitir nulo. Ahora es el score promedio × 100
--      del estado diagnosticado, y un diagnóstico No concluyente no lleva score.
--      Sin este script, el alta de un No concluyente falla con una violación de
--      `NOT NULL` (500) en lugar de registrarse.
--
--  QUÉ SE PIERDE
--    Nada. Las filas existentes conservan su valor; las anteriores al cambio
--    quedan con `confidence` nulo (la vista las muestra "sin nivel") y
--    `detection_count` nulo (se lee como 0).
--
--  ORDEN DE EJECUCIÓN
--    Se puede correr antes o después de arrancar la versión nueva (si la tabla
--    todavía no existe, no hay nada que migrar: la crea Hibernate ya nullable).
--    Es idempotente: quitar un NOT NULL que ya no está no falla.
--
--  Rollback: el backend anterior lee `conf` en un `double` y no tolera nulos.
--  Antes de volver atrás:  UPDATE diagnostico SET conf = 0 WHERE conf IS NULL;
-- ============================================================================

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'diagnostico') THEN
        ALTER TABLE diagnostico ALTER COLUMN conf DROP NOT NULL;
    END IF;
END
$$;

COMMIT;

-- ----------------------------------------------------------------------------
-- Verificación (debe devolver 'YES'):
--
--   SELECT is_nullable FROM information_schema.columns
--   WHERE table_name = 'diagnostico' AND column_name = 'conf';
-- ----------------------------------------------------------------------------
