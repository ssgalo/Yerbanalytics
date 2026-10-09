# Change: add-secuencias-demo-expo

## Why

En la expo se quiere mostrar que el hardware responde, y hoy no hay forma de comandar un actuador
a mano: no existe endpoint, el backend no escucha el ACK de los actuadores (`CLAUDE.md` §6) y
`vivero_esp32_red` sólo escucha el riel. La decisión ya está tomada en
`docs-motor-reglas-e-integracion/analisis-demo-expo-vs-vivero.md` §9: tres **secuencias guionadas**
en la pestaña Demo Expo, calcadas de la pasada del riel.

## What

- **Secuencias** en el backend (`POST /api/secuencias`, `GET /api/secuencias/actual`,
  `POST /api/secuencias/actual/cancelar`), con plan fijo de pasos, estado en memoria y timeout por paso:
  - **Riego**: ABRIR (`valve ON`) → ESPERAR N s → CERRAR (`valve OFF`).
  - **Mediasombra**: DESPLEGAR (`shade SET 0`) → ESPERAR N s → ENROLLAR (`shade SET 100`).
  - **Lectura**: PEDIR ("leer ahora") → ESPERAR TELEMETRÍA → MOSTRAR.
- **Comandan el actuador directo, sin pasar por el motor de reglas.** Una a la vez, y ninguna mientras
  corre una pasada: un **guardia compartido** del único ESP32 responde 409. Cancelar deja el actuador
  seguro (válvula cerrada, mediasombra enrollada).
- **Ingesta del ACK** de actuadores (`nursery/zone/+/sector/+/ack`), con adaptador propio.
- **Contrato "leer ahora"** (nuevo): comando de zona `nursery/zone/{zonaId}/command`, espejado en
  `contrato.h`, `ContratoNodo.java` y `simulador/server/contract.ts`.
- **Demo Expo**: tres tarjetas con iniciar/cancelar y progreso por polling; versión mock.
- **Firmware `vivero_esp32_red`**: válvula sobre el driver de bomba, mediasombra a final de carrera,
  ACK y "leer ahora" con la lectura de sensores como **hueco marcado**.

## Fuera de alcance

- Que el motor dispare o vea las secuencias; interacción con `ManualLockRule`.
- Escenarios del simulador (§5.3), perfil de parámetros "expo", `lat/lon` de Buenos Aires.
- Comportamiento del simulador ante "leer ahora" (sólo se espeja el contrato).
- Leer sensores reales en el firmware (no se sabe cuáles van conectados).
- Persistir el historial de secuencias; `pump` (insumos) en el sketch.

## Capabilities

| Tipo | Capability | Qué cubre |
|---|---|---|
| Nueva | `secuencias-guionadas` | API, las tres secuencias, guardia del hardware, cancelación, timeouts, ACK, correlación de la lectura |
| Nueva | `contrato-nodo-mqtt` | Comando "leer ahora", ACK de actuadores y el firmware `vivero_esp32_red` que los cumple |
| Modificada | `demo-expo` (de `add-pasada-riel`, sin archivar) | Vista de las secuencias |

## Rollback

Revertir los commits del cambio. El motor, la telemetría y la pasada no cambian de comportamiento; el
único toque a la pasada (consultar el guardia al iniciar) se revierte con ella. Sin tablas nuevas.

## Criterios de éxito

- [ ] `git diff main -- .../backend/engine` vacío y `./mvnw test` en verde.
- [ ] Iniciar una secuencia con una pasada en curso (o al revés) responde 409.
- [ ] Con hardware (desde el 10/10): riego y mediasombra completan con ACK `SUCCESS`; cancelar deja la
      bomba apagada y la mediasombra enrollada.
- [ ] En `dev:demo` las tres secuencias avanzan solas.
