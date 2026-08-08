-- ============================================================================
--  Migración manual · baja del estado del simulador en la base del vivero
--  (openspec/changes/extract-simulador-standalone)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. `ddl-auto=update` crea y modifica tablas,
--  pero NUNCA las elimina: al quitar las entidades del backend, `modo_operacion`
--  y `sensor_simulado` quedan huérfanas en la base y ahí se quedan.
--
--  QUÉ SE DA DE BAJA Y POR QUÉ
--    · `modo_operacion`  — el modo estático/simulación dejó de ser estado del
--      sistema. Ahora es `VITE_DATA_SOURCE` del dashboard, que se resuelve al
--      arrancar: `http` es el sistema real y `mock` la demo ilustrativa.
--    · `sensor_simulado` — los sensores simulados son estado del SIMULADOR, no
--      del vivero. Viven en `Desarrollo/simulador/data/`, que se borra junto
--      con esa carpeta.
--
--  QUÉ SE PIERDE
--    Sensores de prueba y un modo que ya no existe. NINGÚN dato del vivero:
--    zonas, sectores, lecturas, hardware, historial, capturas y diagnósticos no
--    se tocan. Los sensores de prueba se vuelven a dar de alta en el simulador.
--
--  ORDEN DE EJECUCIÓN
--    1. Detener la app.
--    2. Correr este script.
--    3. Volver a arrancar.
--
--  Es irreversible en cuanto a datos. El ESQUEMA sí se recrea solo si se
--  revierte el código, porque `ddl-auto=update` vuelve a crear las tablas a
--  partir de las entidades.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Modo de operación. Fila única (id = 1) que gobernaba de qué fuente leía el
--    dashboard. El backend ya no tiene modos: se comporta siempre como en
--    producción.
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS modo_operacion;

-- ----------------------------------------------------------------------------
-- 2. Sensores simulados. Eran emisores de prueba, deliberadamente separados del
--    registro de hardware (`dispositivo`), que NO se toca: los nodos reales
--    siguen registrados como estaban.
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS sensor_simulado;

COMMIT;

-- ----------------------------------------------------------------------------
-- Verificación (debe devolver 0 filas):
--
--   SELECT table_name FROM information_schema.tables
--   WHERE table_schema = 'public'
--     AND table_name IN ('modo_operacion', 'sensor_simulado');
-- ----------------------------------------------------------------------------
