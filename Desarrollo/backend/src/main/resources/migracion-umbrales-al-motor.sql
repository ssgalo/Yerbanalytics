-- ============================================================================
--  Migración manual · Configuración → Umbrales queda sólo con las bandas de
--  alerta: el seguimiento post-acción pasa al catálogo del motor y se borra la
--  dosis máxima de insumo (openspec/changes/add-motor-reglas-operativo, D-14)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. `ddl-auto=update` agrega columnas y tablas
--  pero NUNCA baja columnas: al sacar tres campos de la entidad, siguen en
--  `configuracion_operativa` como `NOT NULL` sin default, y el INSERT de una
--  fila operativa nueva falla en PostgreSQL. (El backend arranca igual: la fila
--  existente sólo se actualiza, y `data.sql` ya no intenta insertar si hay una.)
--
--  QUÉ SE MUEVE Y POR QUÉ
--    · `seguimiento_latencia_min` → parámetro `seguimiento.latencia-min` (min)
--    · `seguimiento_delta_min`    → parámetro `seguimiento.delta-min`
--    Los lee `HistorialService` para decidir cuánto espera antes de evaluar si
--    una acción surtió efecto y cuánto tiene que recuperarse la métrica. Son
--    umbrales que deciden, así que pasaron al catálogo, que es la única fuente de
--    umbrales del motor. Se editan en Motor de reglas (`PUT /api/rules/parametros`).
--
--    · `insumo_dosis_max_24h_ml` → se BORRA, no se copia. Nadie decidía con él (el
--      tope que compara `DailyDoseLimitRule` es `insumo.max-dosis-24h`, del
--      catálogo) y duplicaba a ese parámetro.
--
--  QUÉ SE PIERDE
--    Nada que se use. Cada valor de seguimiento se copia a `parametro_regla`
--    SÓLO si difiere de la fábrica (2 min y 5), está dentro del rango del
--    catálogo (1-1440 min y 0,5-50) y todavía no hay una fila de esa clave (un
--    override ya hecho por la API nunca se pisa). El delta se redondea a un
--    decimal, como lo exige el catálogo. Si un valor queda afuera del rango, NO
--    se copia (vale la fábrica) y el script avisa con un `RAISE NOTICE`. Si
--    coincide con la fábrica no hace falta fila: "modificado" significa
--    "hay override".
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
    latencia double precision;
    delta    double precision;
    delta_redondeado numeric;
    dosis    double precision;
BEGIN
    -- Los overrides van a `parametro_regla`: si la app todavía no arrancó con el código nuevo, no existe.
    IF to_regclass('public.parametro_regla') IS NULL THEN
        RAISE EXCEPTION 'Falta la tabla parametro_regla: arrancar la app una vez con el código nuevo antes de correr este script.';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'configuracion_operativa'
                 AND column_name = 'seguimiento_latencia_min') THEN
        SELECT seguimiento_latencia_min INTO latencia FROM configuracion_operativa WHERE id = 1;
        IF latencia IS NOT NULL THEN
            IF latencia < 1 OR latencia > 1440 THEN
                RAISE NOTICE 'seguimiento.latencia-min: el valor guardado (%) está fuera del rango 1-1440 min; no se copia, rige la fábrica (2 min).', latencia;
            ELSIF latencia <> 2 THEN
                -- Fábrica 2 min, rango 1-1440, entero.
                INSERT INTO parametro_regla (clave, valor, updated_by, updated_ts)
                VALUES ('seguimiento.latencia-min', ROUND(latencia)::numeric::text, 'migracion-umbrales-al-motor',
                        (EXTRACT(EPOCH FROM now()) * 1000)::bigint)
                ON CONFLICT (clave) DO NOTHING;
            END IF;
        END IF;
        ALTER TABLE configuracion_operativa DROP COLUMN seguimiento_latencia_min;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'configuracion_operativa'
                 AND column_name = 'seguimiento_delta_min') THEN
        SELECT seguimiento_delta_min INTO delta FROM configuracion_operativa WHERE id = 1;
        IF delta IS NOT NULL THEN
            delta_redondeado := ROUND(delta::numeric, 1);
            IF delta_redondeado < 0.5 OR delta_redondeado > 50 THEN
                RAISE NOTICE 'seguimiento.delta-min: el valor guardado (%) está fuera del rango 0,5-50; no se copia, rige la fábrica (5).', delta;
            ELSIF delta_redondeado <> 5 THEN
                IF delta_redondeado <> delta::numeric THEN
                    RAISE NOTICE 'seguimiento.delta-min: el valor guardado (%) se redondeó a % (un decimal).', delta, delta_redondeado;
                END IF;
                -- Formato canónico del catálogo: sin ceros a la derecha ("8.5", "10").
                INSERT INTO parametro_regla (clave, valor, updated_by, updated_ts)
                VALUES ('seguimiento.delta-min', regexp_replace(delta_redondeado::text, '\.0$', ''),
                        'migracion-umbrales-al-motor', (EXTRACT(EPOCH FROM now()) * 1000)::bigint)
                ON CONFLICT (clave) DO NOTHING;
            END IF;
        END IF;
        ALTER TABLE configuracion_operativa DROP COLUMN seguimiento_delta_min;
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
--   -- 2. Los overrides migrados (sólo los que diferían de fábrica):
--   SELECT * FROM parametro_regla WHERE clave IN ('seguimiento.latencia-min', 'seguimiento.delta-min');
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- BLOQUE INVERSO (rollback del código). Descomentar sólo si se revierte el
-- cambio y se vuelve a la entidad con las tres columnas. Se agregan con DEFAULT
-- (los valores de fábrica) para que el ALTER no falle sobre filas existentes, y
-- se recuperan los overrides del catálogo si los hubiera.
--
--   BEGIN;
--   ALTER TABLE configuracion_operativa
--       ADD COLUMN IF NOT EXISTS insumo_dosis_max_24h_ml double precision NOT NULL DEFAULT 15,
--       ADD COLUMN IF NOT EXISTS seguimiento_latencia_min integer NOT NULL DEFAULT 2,
--       ADD COLUMN IF NOT EXISTS seguimiento_delta_min double precision NOT NULL DEFAULT 5;
--
--   UPDATE configuracion_operativa SET seguimiento_latencia_min = p.valor::integer
--   FROM parametro_regla p WHERE p.clave = 'seguimiento.latencia-min';
--
--   UPDATE configuracion_operativa SET seguimiento_delta_min = p.valor::double precision
--   FROM parametro_regla p WHERE p.clave = 'seguimiento.delta-min';
--   COMMIT;
-- ----------------------------------------------------------------------------
