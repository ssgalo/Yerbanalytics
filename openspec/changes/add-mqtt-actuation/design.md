# Design: add-mqtt-actuation

## Architecture Overview
La arquitectura de downlink utiliza Spring Integration de forma simétrica a la ingesta de telemetría.
1. **MqttGateway**: Una interfaz `@MessagingGateway` que expone un método para enviar mensajes a canales de Spring Integration de forma transparente.
2. **MqttOutboundChannel**: Un canal dedicado a los mensajes salientes hacia el broker MQTT.
3. **MqttPahoMessageHandler**: Un adapter que suscribe a `MqttOutboundChannel` y publica los mensajes al broker utilizando el cliente Eclipse Paho, configurado con QoS 2.
4. **ActionExecutor**: En lugar de solo loguear las acciones, construirá un JSON (usando `ObjectMapper`) con el formato requerido por `contrato.h` y lo enviará a través de `MqttGateway`.

## Payload Structure
El payload JSON enviado al broker (tópico `nursery/zone/{zonaId}/sector/{sectorId}/command`) tendrá la siguiente estructura (basada en `contrato.h`):
```json
{
  "commandId": "uuid-v4",
  "actuador": "valve",
  "accion": "ON",
  "parametros": {
    "durationSec": 600
  }
}
```

- `commandId`: UUID para permitir la idempotencia y control de recepción en el nodo físico.
- `actuador`: Puede ser `valve`, `pump`, o `shade`.
- `accion`: `ON`, `OFF` (en el caso de shade, se transmite directamente un % pero el contrato permite "accion" u omitirla y enviar "targetPct").
- `parametros`: Para la válvula, `durationSec` proviene del motivo que emite `RiegoRule` (ej: `[tiempo-max-seg=600]`).

## Simulator Changes
El simulador modificará su archivo `mqtt.ts`:
- En la función `connect()`, tras conectarse, suscribirse a `nursery/zone/+/sector/+/command`.
- Escuchar el evento `message`.
- Registrar por consola cuando reciba una orden para asegurar que el backend emite correctamente el downlink sin necesidad de usar la placa física de inmediato.
