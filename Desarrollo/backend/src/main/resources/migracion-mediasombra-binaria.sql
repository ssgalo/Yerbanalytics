-- ============================================================================
--  Migración manual · mediasombra binaria (openspec/changes/fix-mediasombra-binaria)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. El backend ARRANCA IGUAL sin correrlo: un
--  override de una clave que ya no existe se ignora con un warn.
--
--  QUÉ CAMBIA
--    · El hardware de la mediasombra es binario (abierta / cerrada), así que salen
--      dos claves del catálogo de parámetros:
--        mediasombra.apertura-proteccion-uv
--        mediasombra.apertura-maxima
--    · Queda `mediasombra.uv-umbral`: con pico de UV la regla cierra la mediasombra.
--
--  Idempotente: borrar overrides que no existen no hace nada.
-- ============================================================================

DELETE FROM parametro_regla
 WHERE clave IN ('mediasombra.apertura-proteccion-uv', 'mediasombra.apertura-maxima');
