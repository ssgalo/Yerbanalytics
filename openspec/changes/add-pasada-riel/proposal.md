# Change: add-pasada-riel

## Why

La cadena de captura ya funciona de punta a punta salvo un eslabón: **nadie mueve el riel**. La
orden de captura existe (`CapturaService.emitirOrden`, `CapturaService.java:105`), la app Android la
recibe por SSE y sube la foto, y el servicio de inferencia diagnostica. Pero el riel sólo se mueve a
mano por monitor serie (`prototipo_hardware/vivero_esp32/vivero_esp32.ino`, sin WiFi ni MQTT), y el
contrato MQTT no lo contempla (`prototipo_hardware/README.md`: "Riel de la cámara: existe sólo acá").

Para la expo hace falta disparar desde el dashboard el flujo **real**: front → backend → broker →
ESP32 → celular → inferencia → "Diagnósticos de IA".

## What

- **Contrato MQTT del riel** (nuevo): comando `nursery/rail/command` y evento `nursery/rail/event`,
  con `commandId` para correlación e idempotencia. Se espeja en `contrato.h`, `ContratoRiel.java`,
  `simulador/server/contract.ts` y el sketch.
- **Planificador de pasadas** en el backend: la capacidad que el `CLAUDE.md` ya anuncia como futuro
  emisor de órdenes de captura. Una pasada = ir a posición 1 → foto del sector 1 → posición 2 → foto
  del sector 2 → home. Una sola a la vez, estado en memoria, endpoints `POST /api/pasadas`,
  `GET /api/pasadas/actual`, `POST /api/pasadas/actual/cancelar`. No es "de demo": el backend no
  tiene modos.
- **Interruptor "Demo Expo"** persistido en el backend (`GET/PUT /api/configuracion/demo-expo`), que
  muestra u oculta la pestaña sin reiniciar nada.
- **Pestaña "Demo Expo"** del dashboard: iniciar/cancelar, progreso paso a paso por polling,
  miniatura de cada foto y estado de su diagnóstico. En modo `mock` simula la pasada.
- **Firmware `vivero_esp32_red`**: copia del sketch del prototipo con WiFi + MQTT, que sólo escucha
  el comando del riel. El original no se toca.

## Fuera de alcance

- Que el motor de reglas dispare pasadas (el disparo es manual). El motor no cambia.
- Válvula, bomba y mediasombra por MQTT en el sketch nuevo.
- Cambios en la app de cámara o en `/api/camara/v1/**`.
- Persistir el historial de pasadas (las órdenes y capturas, que son el registro real, ya se persisten).
- Un tópico de presencia del ESP32 (LWT): la falta de `ACEPTADO` ya detecta el riel caído.
