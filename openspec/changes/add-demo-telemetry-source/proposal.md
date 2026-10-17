# Change: add-demo-telemetry-source

> Rama: `feat/demo-telemetry-source`. Sigue el patrón de modo demo de `add-control-demo-reglas`.

## Why

En modo demo, el ESP32 real y el simulador publican la telemetría de la misma zona en el **mismo tópico**
(`nursery/zone/{z}/telemetry`): las lecturas se pisan y no se sabe cuál usó el motor. Se necesita que el simulador
escriba en un tópico **aparte** y que el operador elija, desde el simulador, qué telemetría lee el backend.

## What Changes

- **Tópico de demo** `nursery/demo/zone/{zonaId}/telemetry` (árbol separado: el comodín productivo
  `nursery/zone/+/telemetry` nunca lo alcanza). Mismo payload y unidades que el firmware.
- **Fuente de telemetría** en memoria: `REAL_NODE` (por defecto) | `SIMULATOR`, con `GET/PUT /api/demo/telemetry-source`.
- **Compuerta**: con `SIMULATOR` el backend procesa *sólo* la telemetría del tópico de demo; con `REAL_NODE`, sólo la
  real. Vale para la ingesta del motor, el pulso de sensado y la lectura de Demo Expo.
- **Simulador**: en modo demo publica todo en el tópico de demo y suma un interruptor "Telemetría que usa el backend".
- Lo demás no cambia: LEER_AHORA y los comandos a actuadores siguen en los tópicos del ESP32; el ACK no cambia.

## Out of scope

Firmware y `contrato.h`; persistir la fuente (se pierde al reiniciar, como los demás overrides de demo).
