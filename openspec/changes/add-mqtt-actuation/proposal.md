# Change: add-mqtt-actuation

## Why
El sistema actualmente evalúa las reglas (ej: `RiegoRule`) y decide qué acciones tomar (ej: `ACTIVAR_VALVULA`), registrándolas en la base de datos y el historial. Sin embargo, para completar el ciclo ciberfísico de la **Release 1** (Riego Autónomo), es indispensable transmitir físicamente esa orden de vuelta a los nodos en el vivero (Downlink).
Dado que el nodo actuador ESP32 ya está diseñado para suscribirse y escuchar comandos, el backend debe habilitar su capacidad de publicar comandos MQTT. Además, el simulador debe actualizarse para suscribirse a estos comandos y probar el ciclo completo sin hardware.

## What Changes
- **Backend (MQTT Outbound)**: Configuración de un `MessageChannel` de salida y un `@MessagingGateway` en Spring Integration (`MqttConfig`).
- **Backend (ActionExecutor)**: Inyección del gateway para construir y enviar el JSON del comando al tópico correspondiente del sector cada vez que se ejecute una acción.
- **Simulador**: Suscripción al tópico de comandos (`nursery/zone/+/sector/+/command`) y registro de la llegada de órdenes, emulando la respuesta física.

## Impact
- **Affected specs:** `backend-core`, `telemetry-contract`.
- **Affected code:** `ActionExecutor.java`, `MqttConfig.java`, `simulador/server/mqtt.ts`.
- **Hardware:** Completa el contrato definido en `embebido/comun/contrato.h`.

## Non-Goals
- No se modifica la lógica de toma de decisión del motor de reglas, ya que está lista en `RiegoRule` y otras.
- No se envían confirmaciones cruzadas (el Ack ya lo gestiona el ESP32, el backend por ahora solo envía con QoS 2).
