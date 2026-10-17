## ADDED Requirements

Todo lo de este cambio existe **sólo** con `yerbanalytics.demo.enabled=true`.

### Requirement: Fuente de telemetría seleccionable
`GET/PUT /api/demo/telemetry-source` SHALL leer y fijar la fuente (`REAL_NODE` | `SIMULATOR`); por defecto `REAL_NODE`.
Un valor inválido SHALL responder 400.

#### Scenario: Producción
- **WHEN** el backend corre sin modo demo
- **THEN** `/api/demo/telemetry-source` responde 404 y no existe suscripción a `nursery/demo/zone/+/telemetry`

### Requirement: Compuerta por fuente
Con `SIMULATOR` el backend SHALL procesar sólo la telemetría de `nursery/demo/zone/{z}/telemetry`; con `REAL_NODE`, sólo la
de `nursery/zone/{z}/telemetry`. La descartada no se persiste ni cuenta como respuesta del pulso ni de la lectura de Demo Expo.

#### Scenario: Fuente simulador
- **WHEN** la fuente es `SIMULATOR` y llegan lecturas de ambos tópicos
- **THEN** sólo la del tópico de demo llega al motor

### Requirement: El simulador publica aparte
Con el modo demo del backend disponible, el simulador SHALL publicar toda su telemetría en el tópico de demo y ofrecer un
interruptor para elegir la fuente; sin modo demo, publica en el tópico regular. Los comandos siguen en los tópicos del ESP32.
