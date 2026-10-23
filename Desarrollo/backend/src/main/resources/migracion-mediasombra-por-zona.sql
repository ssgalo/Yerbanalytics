-- ============================================================
-- Migración manual: la mediasombra es de la macro-zona (add-rustificacion-por-macrozona).
--
-- Antes la malla se registraba como actuador de un sector y su posición vivía en
-- sector.actuador_shade; la rustificación era global (rustificacion_etapa, % de apertura).
-- Ahora la mediasombra se registra como el nodo testigo (zona_id, sin sector_id; una por zona),
-- su posición vive en zona.mediasombra_abierta y el plan de rustificación es por macro-zona
-- (plan_rustificacion / etapa_plan_rustificacion, que crea Hibernate).
--
-- ddl-auto=update no mueve datos ni borra columnas: correr a mano UNA vez, con el backend
-- nuevo ya arrancado al menos una vez (para que exista zona.mediasombra_abierta). Idempotente.
-- ============================================================

BEGIN;

-- 1. Por cada zona sin mediasombra de zona, la primera mediasombra de sus sectores pasa a ser la
--    de la zona (conserva serial y falla).
UPDATE dispositivo d
SET zona_id = s.zona_id, sector_id = NULL
FROM sector s
WHERE d.tipo = 'mediasombra'
  AND d.sector_id = s.id
  AND d.id = (
      SELECT d2.id FROM dispositivo d2 JOIN sector s2 ON s2.id = d2.sector_id
      WHERE d2.tipo = 'mediasombra' AND s2.zona_id = s.zona_id
      ORDER BY d2.id LIMIT 1)
  AND NOT EXISTS (
      SELECT 1 FROM dispositivo z
      WHERE z.tipo = 'mediasombra' AND z.zona_id = s.zona_id AND z.sector_id IS NULL);

-- 2. Las mediasombras que quedaron colgadas de un sector sobran: una malla por zona.
DELETE FROM dispositivo WHERE tipo = 'mediasombra' AND sector_id IS NOT NULL;

-- 3. La posición pasa a la zona: abierta si la mayoría de sus sectores decía >= 50 %.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'sector' AND column_name = 'actuador_shade') THEN
        UPDATE zona z SET mediasombra_abierta = sub.abierta
        FROM (SELECT zona_id, AVG(actuador_shade) >= 50 AS abierta FROM sector GROUP BY zona_id) sub
        WHERE sub.zona_id = z.id AND z.mediasombra_abierta IS NULL;
        ALTER TABLE sector DROP COLUMN actuador_shade;
    END IF;
END $$;

-- 4. La rustificación global por % de apertura se va: ahora hay un plan por macro-zona.
DROP TABLE IF EXISTS rustificacion_etapa;

COMMIT;
