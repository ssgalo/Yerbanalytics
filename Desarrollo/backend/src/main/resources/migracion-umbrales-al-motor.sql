-- ============================================================================
--  Migración manual · Configuración → Umbrales queda sólo con las bandas de
--  alerta: se borran las columnas del seguimiento post-acción y la dosis máxima
--  de insumo (openspec/changes/add-motor-reglas-operativo, D-14)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. `ddl-auto=update` agrega columnas y tablas
--  pero NUNCA baja columnas: al sacar tres campos de la entidad, siguen en
--  `configuracion_operativa` como `NOT NULL` sin default, y el INSERT de una
--  fila operativa nueva falla en PostgreSQL. (El backend arranca igual: la fila
--  existente sólo se actualiza, y `data.sql` ya no intenta insertar si hay una.)
--
--  QUÉ SE BORRA Y POR QUÉ (todo se descarta, nada se copia)
--    · `seguimiento_latencia_min` y `seguimiento_delta_min`: el seguimiento
--      post-acción por humedad fija lo reemplazó el modelo de efectividad
--      (reglas_v2 E-01…E-03), que usa los parámetros `efectividad.*` del
--      catálogo. Ya nadie lee estos dos valores.
--    · `insumo_dosis_max_24h_ml`: nadie decidía con él (el tope que compara
--      `DailyDoseLimitRule` es `insumo.max-dosis-24h`, del catálogo) y duplicaba
--      a ese parámetro.
--    Si en `parametro_regla` quedó alguna fila `seguimiento.*` de una versión
--    intermedia, se borra también: la clave ya no existe en el catálogo.
--
--  ORDEN DE EJECUCIÓN
--    1. Arrancar la app una vez con el código nuevo → crea `parametro_regla` y
--       `regla_estado`.
--    2. Detener la app.
--    3. Correr este script.
--    4. Volver a arrancar.
--
--  El script es idempotente: si las columnas ya no existen, no hace nada; nunca
--  pisa un override existente.
--
--  Reversión: ver el bloque comentado al final. `ddl-auto=update` NO puede
--  volver a agregar las columnas por su cuenta: intentaría `NOT NULL` sin
--  default sobre una tabla con filas y PostgreSQL lo rechaza.
-- ============================================================================

BEGIN;

DO $$
DECLARE
    dosis    double precision;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'configuracion_operativa'
                 AND column_name = 'seguimiento_latencia_min') THEN
        ALTER TABLE configuracion_operativa DROP COLUMN seguimiento_latencia_min;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'configuracion_operativa'
                 AND column_name = 'seguimiento_delta_min') THEN
        ALTER TABLE configuracion_operativa DROP COLUMN seguimiento_delta_min;
    END IF;

    IF to_regclass('public.parametro_regla') IS NOT NULL THEN
        DELETE FROM parametro_regla WHERE clave IN ('seguimiento.latencia-min', 'seguimiento.delta-min');
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'configuracion_operativa'
                 AND column_name = 'insumo_dosis_max_24h_ml') THEN
        SELECT insumo_dosis_max_24h_ml INTO dosis FROM configuracion_operativa WHERE id = 1;
        IF dosis IS NOT NULL AND dosis <> 15 THEN
            RAISE NOTICE 'insumo_dosis_max_24h_ml = % se descarta: nadie decidía con él. El tope vigente es insumo.max-dosis-24h del catálogo.', dosis;
        END IF;
        ALTER TABLE configuracion_operativa DROP COLUMN insumo_dosis_max_24h_ml;
    END IF;
END
$$;

COMMIT;

-- ----------------------------------------------------------------------------
-- Verificación:
--
--   -- 1. Las columnas ya no existen (debe devolver 0 filas):
--   SELECT column_name FROM information_schema.columns
--   WHERE table_name = 'configuracion_operativa'
--     AND column_name IN ('insumo_dosis_max_24h_ml', 'seguimiento_latencia_min', 'seguimiento_delta_min');
--
--   -- 2. No quedan overrides del seguimiento viejo (debe devolver 0 filas):
--   SELECT * FROM parametro_regla WHERE clave LIKE 'seguimiento.%';
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- BLOQUE INVERSO (rollback del código). Descomentar sólo si se revierte el
-- cambio y se vuelve a la entidad con las tres columnas. Se agregan con DEFAULT
-- (los valores de fábrica) para que el ALTER no falle sobre filas existentes, y
-- (los overrides del seguimiento viejo no se recuperan: se descartaron).
--
--   BEGIN;
--   ALTER TABLE configuracion_operativa
--       ADD COLUMN IF NOT EXISTS insumo_dosis_max_24h_ml double precision NOT NULL DEFAULT 15,
--       ADD COLUMN IF NOT EXISTS seguimiento_latencia_min integer NOT NULL DEFAULT 2,
--       ADD COLUMN IF NOT EXISTS seguimiento_delta_min double precision NOT NULL DEFAULT 5;
--
--   COMMIT;
-- ----------------------------------------------------------------------------
