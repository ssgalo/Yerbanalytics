-- ============================================================================
--  Migración manual · mudanza de dos columnas de `configuracion_operativa` al
--  catálogo de parámetros de reglas, y copia del umbral de riego editado
--  (openspec/changes/add-catalogo-umbrales-reglas)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. `ddl-auto=update` agrega columnas y tablas
--  pero NUNCA baja columnas: al sacar `riego_tiempo_max_seg` y
--  `mediasombra_apertura_max_pct` de la entidad, siguen en la tabla como
--  `NOT NULL` sin default, y el primer INSERT de una fila operativa nueva
--  falla en PostgreSQL. (El backend arranca igual: los valores de fábrica del
--  catálogo son los mismos que traía el seed, y la fila existente sólo se lee.)
--
--  QUÉ SE MUEVE Y POR QUÉ
--    · `riego_tiempo_max_seg`          → parámetro `riego.tiempo-max-apertura` (s)
--    · `mediasombra_apertura_max_pct`  → parámetro `mediasombra.apertura-maxima` (%)
--    Eran umbrales que una regla compara (IrrigationRule, ShadingRule), así que
--    pasaron al catálogo, que es la única fuente de umbrales de reglas. Se
--    editan desde `PUT /api/rules/parametros`.
--
--    · `umbral_metrica.ideal_min` de `humSus` → parámetro `riego.umbral-humedad` (%)
--    Antes `IrrigationRule` regaba por debajo del `ideal_min` de la humedad de
--    sustrato (editable en la configuración agronómica); ahora lo hace por
--    `riego.umbral-humedad` (rango 35-60). La fábrica de ese parámetro era 42 y
--    HOY es 45 (cambio implement-reglas-riego): se compara contra la fábrica
--    ACTUAL (45). Si en la base ese `ideal_min` es distinto de 45 —incluido el
--    42 que traía el seed—, sin copiarlo el riego cambiaría en silencio de 42
--    (o del valor editado) a 45; con el override se conserva el valor que la
--    base ya tenía.
--    El `ideal_min` sigue existiendo como umbral de estado (colorea el mapa); lo
--    que se copia es su valor, una sola vez, a un override.
--
--  QUÉ SE PIERDE
--    Nada. Cada valor se copia a `parametro_regla` SÓLO si difiere del de
--    fábrica (120 s y 100 %), redondeado y acotado al rango del catálogo
--    (10-600 s y 10-100 %); el umbral de riego, a 35-60 sin decimales, con un
--    `RAISE NOTICE` si hubo que acotarlo. Si coincide con la fábrica no hace falta fila:
--    "modificado" significa "hay override".
--
--  ORDEN DE EJECUCIÓN
--    1. Arrancar la app una vez con el código nuevo → crea `parametro_regla`.
--    2. Detener la app.
--    3. Correr este script.
--    4. Volver a arrancar.
--
--  El script es idempotente: si las columnas ya no existen, no hace nada; el
--  umbral de riego nunca pisa un override existente.
--
--  Reversión: ver el bloque comentado al final. `ddl-auto=update` NO puede
--  volver a agregar las columnas por su cuenta: intentaría `NOT NULL` sin
--  default sobre una tabla con filas y PostgreSQL lo rechaza.
-- ============================================================================

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'configuracion_operativa'
                 AND column_name = 'riego_tiempo_max_seg') THEN
        -- 1. Copiar a overrides sólo si difiere de la fábrica (120 s), acotado a 10-600.
        INSERT INTO parametro_regla (clave, valor, updated_by, updated_ts)
        SELECT 'riego.tiempo-max-apertura',
               LEAST(GREATEST(ROUND(riego_tiempo_max_seg), 10), 600)::numeric::text,
               'migracion-catalogo-parametros',
               (EXTRACT(EPOCH FROM now()) * 1000)::bigint
        FROM configuracion_operativa
        WHERE id = 1
          AND LEAST(GREATEST(ROUND(riego_tiempo_max_seg), 10), 600) <> 120
        ON CONFLICT (clave) DO NOTHING;
        -- 2. Bajar la columna.
        ALTER TABLE configuracion_operativa DROP COLUMN riego_tiempo_max_seg;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'configuracion_operativa'
                 AND column_name = 'mediasombra_apertura_max_pct') THEN
        -- Fábrica 100 %, rango 10-100.
        INSERT INTO parametro_regla (clave, valor, updated_by, updated_ts)
        SELECT 'mediasombra.apertura-maxima',
               LEAST(GREATEST(ROUND(mediasombra_apertura_max_pct), 10), 100)::numeric::text,
               'migracion-catalogo-parametros',
               (EXTRACT(EPOCH FROM now()) * 1000)::bigint
        FROM configuracion_operativa
        WHERE id = 1
          AND LEAST(GREATEST(ROUND(mediasombra_apertura_max_pct), 10), 100) <> 100
        ON CONFLICT (clave) DO NOTHING;
        ALTER TABLE configuracion_operativa DROP COLUMN mediasombra_apertura_max_pct;
    END IF;
END
$$;

-- Umbral de riego: copiar el `ideal_min` de humSus si difiere de la fábrica ACTUAL (45; era 42) y todavía
-- no hay un override. Idempotente: con el override ya creado (por esto o por la API) no hace nada.
DO $$
DECLARE
    original double precision;
    acotado  numeric;
BEGIN
    SELECT ideal_min INTO original FROM umbral_metrica WHERE metric_key = 'humSus';
    IF original IS NULL THEN
        RETURN;
    END IF;
    acotado := LEAST(GREATEST(ROUND(original::numeric), 35), 60);
    IF acotado <> 45   -- fábrica actual de riego.umbral-humedad (ParametrosRiego.UMBRAL_HUMEDAD)
       AND NOT EXISTS (SELECT 1 FROM parametro_regla WHERE clave = 'riego.umbral-humedad') THEN
        IF acotado <> original::numeric THEN
            RAISE NOTICE 'riego.umbral-humedad: ideal_min de humSus = % se acotó a % (rango 35-60, sin decimales).',
                original, acotado;
        END IF;
        INSERT INTO parametro_regla (clave, valor, updated_by, updated_ts)
        VALUES ('riego.umbral-humedad', acotado::text, 'migracion-catalogo-parametros',
                (EXTRACT(EPOCH FROM now()) * 1000)::bigint);
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
--     AND column_name IN ('riego_tiempo_max_seg', 'mediasombra_apertura_max_pct');
--
--   -- 2. Los overrides migrados (sólo los que diferían de fábrica):
--   SELECT * FROM parametro_regla
--   WHERE clave IN ('riego.tiempo-max-apertura', 'mediasombra.apertura-maxima', 'riego.umbral-humedad');
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- BLOQUE INVERSO (rollback del código). Descomentar sólo si se revierte el
-- cambio y se vuelve a la entidad con las dos columnas. Se agregan con DEFAULT
-- (los valores de fábrica) para que el ALTER no falle sobre filas existentes, y
-- se recuperan los overrides del catálogo si los hubiera.
--
--   BEGIN;
--   ALTER TABLE configuracion_operativa
--       ADD COLUMN IF NOT EXISTS riego_tiempo_max_seg double precision NOT NULL DEFAULT 120,
--       ADD COLUMN IF NOT EXISTS mediasombra_apertura_max_pct double precision NOT NULL DEFAULT 100;
--
--   UPDATE configuracion_operativa SET riego_tiempo_max_seg = p.valor::double precision
--   FROM parametro_regla p WHERE p.clave = 'riego.tiempo-max-apertura';
--
--   UPDATE configuracion_operativa SET mediasombra_apertura_max_pct = p.valor::double precision
--   FROM parametro_regla p WHERE p.clave = 'mediasombra.apertura-maxima';
--   COMMIT;
-- ----------------------------------------------------------------------------
