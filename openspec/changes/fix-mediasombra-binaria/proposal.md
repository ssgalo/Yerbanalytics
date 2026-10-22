## Por qué

Prueba en vivo (17/10): la tarjeta del camino feliz decía «Mediasombra: sin acción — Pico UV activo —
mediasombra ya en posición protectora (30 %)». Dos fallas en `ShadingRule`:

1. Trabajaba en porcentajes (30 % protector, tope `apertura-maxima`), pero el hardware es **binario**:
   el firmware enrolla con un objetivo >= 50 (`MEDIASOMBRA_UMBRAL_APERTURA_PCT`) y desenrolla por debajo.
2. Comparaba el objetivo con `sector.actuadorShade` (lo último que mandó el *backend*) y emitía NOOP si
   coincidía; pero el nodo pudo reiniciarse o moverlo una secuencia de Demo Expo, así que no es la posición real.

## Qué cambia

- La regla pide sólo 100 (abierta) o 0 (cerrada). Pico de UV (`mediasombra.uv-umbral`) → cierra (0).
- Con el pico activo **siempre** emite el comando. El firmware ignora un SET a su posición actual y
  responde `sin_cambio`; se confirma con `act_mediasombra` y ese ack.
- Rama legada del plan de rustificación: el objetivo de la etapa se pasa a binario (>= 50 abierta) y se
  emite sólo si difiere del estado binario guardado. La reemplaza `add-mediasombra-v2`.
- Se retiran `mediasombra.apertura-proteccion-uv` y `mediasombra.apertura-maxima` del catálogo. El tope
  de las etapas del plan pasa a 100 % fijo. Migración manual `migracion-mediasombra-binaria.sql`.
- Mensajes sin porcentajes: «Mediasombra cerrada por pico de radiación UV (índice X ≥ umbral Y).»

## Impacto

Backend (`ShadingRule`, `ParametrosMediasombra`, `ConfiguracionService`), fixture y traza mock del
frontend, simulador (`ruleConditions`, escenarios, lectura del camino feliz).
