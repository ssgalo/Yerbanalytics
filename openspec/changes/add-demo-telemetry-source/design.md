# Design: add-demo-telemetry-source

`be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`; `sim/` = `Desarrollo/simulador/`.

## Decisiones

### D-1 — Mecanismo sin dependencias de demo en el código productivo
Se espeja `RuleOverrides` (interfaz neutra de lectura que consume el código productivo): `be/mqtt/TelemetryAdmission`
(`boolean admits(String topic)`, con `ALL` = admite todo). `MqttTelemetryReceiver` y `LecturaZonaReceiver` la reciben por
`ObjectProvider` y, sin bean, usan `ALL`. La implementación (`DemoTelemetrySourceStore`), el controlador y los adaptadores
MQTT del tópico de demo viven en `be/demo/`, todos con `@ConditionalOnProperty(yerbanalytics.demo.enabled=true)`.
Con demo apagado no hay bean, suscripción ni endpoint (404).

### D-2 — Tópico y constante
`nursery/demo/zone/+/telemetry` se declara en el paquete demo (`DemoTelemetryTopics`), no en `ContratoNodo`. Se espeja en
`sim/server/contract.ts`. La zona se toma como el segmento anterior al último, válido para ambas formas de tópico.

### D-3 — Dos adaptadores extra
Igual que hoy hay dos suscripciones al tópico real (`-inbound` ingesta, `-lectura` secuencias), hay dos al de demo
(`-demo-inbound`, `-demo-lectura`) que vuelcan en los mismos canales, de modo que el procesamiento es idéntico.

### D-4 — Compuerta
`DemoTelemetrySourceStore.admits(topic)`: tópico bajo `nursery/demo/` ⇒ sólo con `SIMULATOR`; cualquier otro ⇒ sólo con
`REAL_NODE`. Lo descartado no se persiste ni cuenta como respuesta del pulso.

### D-5 — Simulador
Si `GET /backend/api/demo/rules` responde 200 (misma detección que los demás controles de demo), el servidor publica toda
la telemetría en el tópico de demo; si no, en el regular. El ACK no cambia.
