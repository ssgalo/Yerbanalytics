# Tasks: add-mqtt-actuation

## Backend
- [x] Definir `MqttCommandGateway` como `@MessagingGateway` con su respectivo `defaultRequestChannel`.
- [x] Modificar `MqttConfig` para crear:
  - El canal `mqttOutboundChannel`.
  - El bean `MqttPahoMessageHandler` conectado a `mqttOutboundChannel` configurado con QoS 1.
- [x] Modificar `ActionExecutor.java`:
  - Inyectar `MqttCommandGateway` y `ObjectMapper`.
  - Parsear atributos relevantes del string del motivo (`[tiempo-max-seg=...]`, `[apertura=...]`).
  - Generar el `commandId` (UUID).
  - Enviar el JSON al tópico correspondiente a la zona y sector del `RuleContext`.

## Simulador
- [x] Actualizar `mqtt.ts`:
  - Suscribirse a `nursery/zone/+/sector/+/command` cuando se conecta al broker.
  - Añadir handler `client.on('message', ...)` para capturar los comandos y emitirlos por consola.
