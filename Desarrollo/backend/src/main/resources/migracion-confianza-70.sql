-- ============================================================================
--  Migración manual · confianza mínima del diagnóstico 85 % → 70 %
--  (openspec/changes/align-reglas-existentes-v2, bloque 1)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NO SE EJECUTA SOLO. El valor de fábrica de
--  `diagnostico.confianza-minima` pasó de 85 a 70 y su rango de 50–100 a
--  50–95 (reglas_v2 §1.3 y §11).
--
--  POR QUÉ EL SCRIPT ES TAN CHICO
--    El catálogo no siembra filas: `parametro_regla` sólo guarda los valores
--    que un usuario cambió a algo distinto de la fábrica (guardar un valor
--    igual a la fábrica borra la fila). Entonces:
--      · Una base que estaba en la fábrica vieja (85) no tiene fila y toma el
--        70 sola, sin hacer nada.
--      · Ninguna fila puede valer "85 sin editar": quien quiso 85 lo guardó
--        cuando la fábrica era 85, y eso borró la fila. Las filas que existen
--        son valores elegidos a propósito (p. ej. 80) y se respetan.
--    Lo único que queda mal son los valores entre 96 y 100, que el rango
--    viejo permitía y el nuevo no: el catálogo los ignora (cae a la fábrica y
--    deja un aviso en el log). Este script los borra para que la fila no
--    quede como inválida.
--
--  QUÉ SE PIERDE
--    Sólo una confianza mínima mayor a 95 %, si alguien la había configurada.
--    Pasa a regir el valor de fábrica (70 %).
--
--  ORDEN DE EJECUCIÓN
--    Idempotente; se puede correr antes o después de arrancar la versión nueva.
--
--  Rollback: volver a la versión anterior del código. La fábrica vuelve a 85.
-- ============================================================================

BEGIN;

DELETE FROM parametro_regla
WHERE clave = 'diagnostico.confianza-minima'
  AND valor ~ '^[0-9]+(\.[0-9]+)?$'
  AND valor::numeric > 95;

COMMIT;

-- ----------------------------------------------------------------------------
-- Verificación (debe devolver 0 filas):
--
--   SELECT clave, valor FROM parametro_regla
--   WHERE clave = 'diagnostico.confianza-minima' AND valor::numeric > 95;
-- ----------------------------------------------------------------------------
