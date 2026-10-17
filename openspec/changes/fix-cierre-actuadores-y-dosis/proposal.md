# Change: fix-cierre-actuadores-y-dosis

> Rama: `fix/pendientes-17-oct`. Tres fallas vistas en vivo con un ESP32-S3 real el 17/10/2026.

## Why

1. **"Regando" que no se apaga.** El estado de la válvula no se guarda: se deriva de `ts + duración + 5 s` del último
   evento "Riego". Si el riego se corta fuera del backend (un `valve OFF` manual, la protección del nodo) el nodo
   manda `ERROR cancelado`, pero `SeguimientoComandosService` lo trata como esperable y lo descarta. El panel sigue
   en "Regando", el despacho no riega ese sector hasta que venza el plazo, y reiniciar no ayuda (se reconstruye del historial).
2. **Bomba colgada en "Dosificando".** `ActionExecutor` pone `Dosificando` y sólo publica `pump ON` si el estado previo
   no era ese. Nada lo devuelve a `En espera`, así que tras la primera dosis la regla decide dosificar y no sale ningún
   comando. Además el ESP32 se reinició a mitad de una dosis (brownout): el ack puede no llegar nunca.
3. **La dosis no la decide el backend.** Se manda `pump ON` con `parametros: {}` y el firmware usa su
   `INSUMO_SEG_POR_DEFECTO` (10 s). El firmware ya acepta `{"ml": X}`.

## What Changes

- Un ack `cancelado` de la válvula cierra el riego en curso del sector y deja una alerta `INFO` `RiegoCortado`
  (con el sector real) en el historial; la reconstrucción tras un reinicio la respeta. `reemplazado` no cierra nada.
- Nuevo `SupplyDoseService`: cierra `Dosificando` al ack final de la bomba (ok, cancelado o error) y, de respaldo,
  por tiempo (duración de la dosis + margen de ack).
- Nuevo parámetro `insumo.dosis-ml` (fábrica 10 ml, 1–50) declarado por `SupplyRule`; el comando sale como
  `pump ON {"ml": N}`. El plazo de seguimiento del comando de la bomba pasa a contar los ml.
- Sin cambios de firmware, de contrato MQTT ni de API pública.

## Out of scope

- Insumos, tanques, precondiciones y cola de aplicaciones: son de `add-dosificacion-base`, que se apoya en esto.
- Aplicar una alerta distinta cuando el riego lo corta el propio backend (estado seguro, OFF de una secuencia): sólo
  se registra el corte si el despacho tenía ese sector regando.
