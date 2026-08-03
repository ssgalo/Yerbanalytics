-- ============================================================================
--  Migración manual · el sensado pasa del sector a la macro-zona
--  (openspec/changes/move-sensado-macrozona)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. `ddl-auto=update` agrega las columnas nuevas
--  en `zona` pero NO copia datos ni borra las columnas viejas de `sector`, y
--  `data.sql` usa `ON CONFLICT DO NOTHING`, así que tampoco corrige umbrales ya
--  sembrados.
--
--  Sólo hace falta si querés conservar el estado de una base existente. Si la
--  base es descartable, borrala y dejá que `data.sql` la siembre de cero.
--
--  ORDEN DE EJECUCIÓN
--    1. Arrancar la app una vez (crea las columnas nuevas en `zona`).
--    2. Detener la app.
--    3. Correr este script.
--    4. Volver a arrancar.
--
--  El paso 1 es necesario: sin las columnas nuevas, el UPDATE del paso 3 falla.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Trasladar la lectura de los sectores a su macro-zona.
--    Los 100 sectores de una zona tenían el mismo valor replicado (la ingesta lo
--    abanicaba), así que alcanza con tomar el del sector que reportó más
--    recientemente. DISTINCT ON exige el ORDER BY que lo acompaña.
-- ----------------------------------------------------------------------------
UPDATE zona z
SET hum_sus_raw       = s.hum_sus_raw,
    hum_amb_raw       = s.hum_amb_raw,
    temp_raw          = s.temp_raw,
    ce_raw            = s.ce_raw,
    uv_raw            = s.uv_raw,
    last_reading_time = s.last_reading_time
FROM (
    SELECT DISTINCT ON (zona_id)
           zona_id, hum_sus_raw, hum_amb_raw, temp_raw, ce_raw, uv_raw, last_reading_time
    FROM sector
    WHERE last_reading_time IS NOT NULL
    ORDER BY zona_id, last_reading_time DESC
) s
WHERE z.id = s.zona_id;

-- ----------------------------------------------------------------------------
-- 2. Estado del nodo testigo: se toma del registro de hardware (HU-21), que ya
--    venía recibiendo el heartbeat de cada nodo.
-- ----------------------------------------------------------------------------
UPDATE zona z
SET nodo_mac     = d.serial,
    nodo_battery = d.bateria,
    nodo_signal  = d.senal
FROM (
    SELECT DISTINCT ON (zona_id) zona_id, serial, bateria, senal
    FROM dispositivo
    WHERE tipo = 'nodo_testigo' AND zona_id IS NOT NULL
    ORDER BY zona_id, ultimo_update DESC NULLS LAST
) d
WHERE z.id = d.zona_id;

-- ----------------------------------------------------------------------------
-- 3. Corregir la unidad de la conductividad.
--    Sólo aplica si la base tiene valores ingeridos por la versión anterior, que
--    persistía el crudo del payload sin convertir. El filtro > 20 distingue los
--    µS/cm sin convertir (orden de 1400) de los dS/m correctos (orden de 1,4):
--    20 dS/m está 6 veces por encima del máximo crítico de la métrica, así que
--    ningún valor legítimo en dS/m cae de ese lado.
-- ----------------------------------------------------------------------------
UPDATE zona SET ce_raw = ce_raw / 1000.0 WHERE ce_raw > 20;

-- ----------------------------------------------------------------------------
-- 4. Re-escala de `uv`: dejó de ser radiación UV en UVI y pasó a ser luminosidad
--    en %. Las bandas viejas (1-6 UVI) marcarían crítico casi cualquier lectura.
--    `data.sql` no puede corregirlo solo: su INSERT hace ON CONFLICT DO NOTHING.
-- ----------------------------------------------------------------------------
UPDATE umbral_metrica
SET ideal_min = 35, ideal_max = 70,
    warn_min  = 20, warn_max  = 85,
    crit_min  = 10, crit_max  = 95
WHERE metric_key = 'uv';

-- Los valores de uv ya persistidos venían en UVI (0-12) y no son convertibles a
-- % de luminosidad: son otra magnitud. Se descartan para no arrastrar un dato
-- que la UI mostraría con la etiqueta equivocada.
UPDATE zona SET uv_raw = NULL;

-- ----------------------------------------------------------------------------
-- 5. Umbrales de las métricas de la sonda de suelo.
--    PROVISIONALES: punto de partida razonable, no criterio agronómico validado.
--    Pendiente de confirmar con el vivero San Ignacio. Mientras tanto son
--    informativas (afectaEstado = false) y no mueven el estado de los sectores.
-- ----------------------------------------------------------------------------
INSERT INTO umbral_metrica (metric_key, ideal_min, ideal_max, warn_min, warn_max, crit_min, crit_max)
VALUES ('tempSuelo', 16,  24,  13,  28,  10,  32),
       ('phSuelo',   5.0, 6.0, 4.5, 6.5, 4.0, 7.0),
       ('n',         100, 200, 70,  260, 40,  320),
       ('p',         30,  60,  20,  80,  10,  100),
       ('k',         120, 240, 90,  300, 60,  380)
ON CONFLICT (metric_key) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 6. Eliminar las columnas obsoletas del sector.
--    Dejalas comentadas hasta verificar que los pasos 1-2 trajeron lo esperado:
--    una vez borradas, no hay vuelta atrás sin backup.
-- ----------------------------------------------------------------------------
-- ALTER TABLE sector DROP COLUMN IF EXISTS hum_sus_raw;
-- ALTER TABLE sector DROP COLUMN IF EXISTS hum_amb_raw;
-- ALTER TABLE sector DROP COLUMN IF EXISTS temp_raw;
-- ALTER TABLE sector DROP COLUMN IF EXISTS ce_raw;
-- ALTER TABLE sector DROP COLUMN IF EXISTS uv_raw;
-- ALTER TABLE sector DROP COLUMN IF EXISTS last_reading_time;

COMMIT;

-- ----------------------------------------------------------------------------
-- Verificación posterior sugerida:
--   SELECT id, hum_sus_raw, ce_raw, uv_raw, nodo_mac, nodo_battery,
--          to_timestamp(last_reading_time / 1000) AS ultima_lectura
--   FROM zona ORDER BY id;
-- ----------------------------------------------------------------------------
