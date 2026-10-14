-- ============================================================================
--  Migración manual · pulso de sensado y pasada diaria
--  (openspec/changes/add-pulso-sensado-y-pasada-diaria)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. `ddl-auto=update` agrega las columnas
--  nuevas (`pasada_diaria_activa`, `pasada_diaria_hora`, `sector.diagnosis_ts`,
--  `sector.diagnosis_usado_ts`, `zona.pulsos_sin_respuesta`), pero NUNCA borra
--  las que el código dejó de usar.
--
--  QUÉ SE DA DE BAJA Y POR QUÉ
--    · `sensing_pull_interval_seconds` — el pedido de lectura cada X segundos
--      se reemplazó por el pulso de sensado, cuyo intervalo es
--      `intervalo_sensado_minutos`. Tener dos intervalos de sensado era lo que
--      hacía correr el motor cada minuto.
--    · `intervalo_evaluacion_minutos` — gobernaba el barrido periódico del
--      motor (`NurseryWatchdog`), que se eliminó: el motor sólo decide en el
--      pulso.
--
--  QUÉ SE PIERDE
--    Dos valores de configuración que ya nadie lee. Ningún dato del vivero.
--
--  ORDEN DE EJECUCIÓN
--    1. Detener la app.
--    2. Correr este script.
--    3. Volver a arrancar.
--
--  Rollback: volver a la versión anterior del código; `ddl-auto=update`
--  recrea las dos columnas vacías y se leen con su valor por defecto.
-- ============================================================================

BEGIN;

ALTER TABLE configuracion_operativa DROP COLUMN IF EXISTS sensing_pull_interval_seconds;
ALTER TABLE configuracion_operativa DROP COLUMN IF EXISTS intervalo_evaluacion_minutos;

COMMIT;

-- ----------------------------------------------------------------------------
-- Verificación (debe devolver 0 filas):
--
--   SELECT column_name FROM information_schema.columns
--   WHERE table_name = 'configuracion_operativa'
--     AND column_name IN ('sensing_pull_interval_seconds', 'intervalo_evaluacion_minutos');
-- ----------------------------------------------------------------------------
