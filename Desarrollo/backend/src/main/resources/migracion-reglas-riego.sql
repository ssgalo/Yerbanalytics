-- ============================================================================
--  Migración manual · reglas de riego v2 (R-01…R-06) y despacho por tandas
--  (openspec/changes/implement-reglas-riego)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. `ddl-auto=update` agrega las columnas nuevas
--  (nulas) pero no limpia filas viejas ni baja nada. El backend ARRANCA IGUAL sin
--  correrlo: un override de una clave que ya no existe se ignora con un warn.
--  Correrlo deja la base sin esas filas huérfanas.
--
--  QUÉ CAMBIA
--    · Salen tres claves del catálogo (las reglas que las leían se borraron):
--        riego.tiempo-max-apertura      → el tiempo sale de volumen ÷ caudal
--        riego.max-riegos-24h           → el bucle lo evita el ciclo de lectura
--        riego.max-riegos-24h-sector      (un riego por sector y ciclo)
--    · `riego.umbral-humedad` pasa de 42 a 45 % y `riego.lluvia-probabilidad` de
--      60 a 70 % DE FÁBRICA. Si en la base NO hay override, el valor vigente cambia
--      solo al desplegar; si lo hay, se respeta (no se toca acá).
--    · `historial_evento` suma `regla`, `alerta`, `volumen_l` y `duracion_seg`, y
--      `zona` suma `hum_sus_ts`: las agrega `ddl-auto=update`, nulas.
--    · `sector.actuador_valve` queda como columna legada: nadie la escribe ni la
--      lee para decidir. El estado de la válvula (Regando / En cola / Cerrada) lo
--      deriva el despacho del último evento "Riego" (ts + duración + 5 s).
--
--  ORDEN DE EJECUCIÓN
--    1. Arrancar la app una vez con el código nuevo → crea las columnas.
--    2. Correr este script (con la app detenida o no: sólo borra filas muertas).
--
--  Idempotente: borrar overrides que no existen no hace nada; el índice usa
--  IF NOT EXISTS.
--
--  Reversión: ver el bloque comentado al final.
-- ============================================================================

BEGIN;

-- 1. Overrides de las tres claves que salen del catálogo.
DELETE FROM parametro_regla
 WHERE clave IN ('riego.tiempo-max-apertura', 'riego.max-riegos-24h', 'riego.max-riegos-24h-sector');

-- 2. Índice para el contexto de riego (último riego / última aplicación por zona, cada 30 s con el nodo real).
--    Opcional: sin él la consulta funciona, sólo más lenta con un historial grande.
CREATE INDEX IF NOT EXISTS idx_historial_evento_zona_tipo_ts
    ON historial_evento (zona_id, tipo, ts);

COMMIT;

-- ----------------------------------------------------------------------------
--  Diagnóstico (sólo lectura): overrides que violan las restricciones nuevas.
--  El backend NO falla al arrancar con ellos (las restricciones cruzadas se validan al guardar), pero
--  la próxima edición desde la pantalla de Reglas los va a rechazar hasta corregirlos. Restricciones:
--    · crítico < umbral de riego < humedad objetivo
--    · saturación de bloqueo ≤ saturación de alerta
--    · volumen máximo ÷ caudal × 3600 ≤ 1200 s (la duración máxima de la válvula)
--  Valores vigentes = override si existe, fábrica si no (35 / 45 / 65 / 75 / 80 / 6 L / 30 L/h):
--
--    WITH v AS (
--      SELECT
--        COALESCE((SELECT valor::numeric FROM parametro_regla WHERE clave = 'riego.umbral-critico'),   35) AS critico,
--        COALESCE((SELECT valor::numeric FROM parametro_regla WHERE clave = 'riego.umbral-humedad'),    45) AS umbral,
--        COALESCE((SELECT valor::numeric FROM parametro_regla WHERE clave = 'riego.humedad-objetivo'),  65) AS objetivo,
--        COALESCE((SELECT valor::numeric FROM parametro_regla WHERE clave = 'riego.saturacion-bloqueo'),75) AS bloqueo,
--        COALESCE((SELECT valor::numeric FROM parametro_regla WHERE clave = 'riego.saturacion-alerta'), 80) AS alerta,
--        COALESCE((SELECT valor::numeric FROM parametro_regla WHERE clave = 'riego.volumen-max-evento'), 6) AS volumen,
--        COALESCE((SELECT valor::numeric FROM parametro_regla WHERE clave = 'riego.caudal-emisor'),     30) AS caudal
--    )
--    SELECT critico, umbral, objetivo, bloqueo, alerta, volumen, caudal,
--           NOT (critico < umbral AND umbral < objetivo)  AS viola_orden_de_humedades,
--           NOT (bloqueo <= alerta)                       AS viola_saturacion,
--           (volumen / caudal * 3600) > 1200              AS viola_duracion_de_valvula
--      FROM v;

-- ----------------------------------------------------------------------------
--  ROLLBACK (comentado). Después de revertir el código:
--    · `actuador_valve` vuelve a leerse: los sectores que quedaron "Regando" del código viejo no se cierran
--      solos, así que se vuelven a "Cerrada".
--    · Las columnas nuevas quedan sin uso (nulas); no hace falta bajarlas.
--
--    BEGIN;
--    UPDATE sector SET actuador_valve = 'Cerrada' WHERE actuador_valve <> 'Cerrada';
--    DROP INDEX IF EXISTS idx_historial_evento_zona_tipo_ts;
--    COMMIT;
