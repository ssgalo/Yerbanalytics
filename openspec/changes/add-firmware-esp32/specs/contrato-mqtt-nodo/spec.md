## ADDED Requirements

### Requirement: Tópicos MQTT a nivel de sector

El contrato SHALL definir tres tópicos MQTT jerárquicos a nivel de sector, coherentes con
`add-rules-engine`: `nursery/zone/{zonaId}/sector/{sectorId}/telemetry` (el nodo publica,
el backend suscribe), `nursery/zone/{zonaId}/sector/{sectorId}/command` (el backend
publica, el nodo suscribe) y `nursery/zone/{zonaId}/sector/{sectorId}/ack` (el nodo
publica). La separación en tres tópicos evita ecos y mantiene payloads limpios.

#### Scenario: Direccionamiento por sector
- **WHEN** el motor de reglas decide accionar un actuador de un sector
- **THEN** publica en `.../sector/{sectorId}/command` y solo el nodo de ese sector lo recibe.

#### Scenario: Telemetría del nodo testigo (nodo_sensor)
- **WHEN** el nodo es un testigo puro por macro-zona (`nodo_sensor`)
- **THEN** publica telemetría en el topic de zona `nursery/zone/{zonaId}/telemetry`
  (compatible con el `MqttTelemetryReceiver` actual, que infiere los 100 sectores), y el
  contrato documenta que el receiver debe migrar a sector-level para el nodo combinado.

### Requirement: Payload de comando

El contrato SHALL definir un payload JSON de comando con, al menos: `actuador`
(`valve` | `pump` | `shade`), `accion` (ej. `open`/`close`/`inject`/`move`), `parametros`
(según actuador: `durationSec`, `ml`, `targetPct`) y opcionalmente `commandId` para
deduplicación defensiva en el nodo. La idempotencia principal la garantiza el backend
(in-flight lock + cooldown).

#### Scenario: Comando de riego
- **WHEN** el backend ordena regar un sector por 30 segundos
- **THEN** publica `{ "actuador": "valve", "accion": "open", "parametros": {
  "durationSec": 30 } }`.

#### Scenario: Comando de dosificación
- **WHEN** el backend ordena inyectar 12 ml
- **THEN** publica `{ "actuador": "pump", "accion": "inject", "parametros": { "ml": 12 } }`.

#### Scenario: Comando de mediasombra
- **WHEN** el backend ordena abrir la mediasombra al 40%
- **THEN** publica `{ "actuador": "shade", "accion": "move", "parametros": {
  "targetPct": 40 } }`.

### Requirement: Payload de ACK con status obligatorio

El contrato SHALL definir el payload del topic de ack con un campo `status` obligatorio de
valor `SUCCESS` o `ERROR`, más detalle opcional (duración real, volumen aplicado, posición
final o tipo de falla). El backend usa este ACK para liberar el in-flight lock y decidir el
cooldown: `SUCCESS` inicia el cooldown, `ERROR` habilita reintento.

#### Scenario: ACK de ejecución
- **WHEN** el nodo termina un comando de riego correctamente
- **THEN** publica `{ "status": "SUCCESS", "detalle": { "durationSec": 30 } }`.

#### Scenario: ACK de falla física
- **WHEN** el nodo detecta falla hidráulica durante el riego
- **THEN** publica `{ "status": "ERROR", "detalle": { "tipo": "falla_hidraulica" } }`.

### Requirement: QoS del canal

El contrato SHALL usar QoS 2 (exactly-once) en el topic de `command` porque una orden
duplicada puede significar doble riego o doble dosis de químico. La telemetría y el ack
SHALL usar al menos QoS 1.

#### Scenario: Comando con QoS 2
- **WHEN** el backend publica un comando de actuador
- **THEN** lo hace con QoS 2, minimizando reentregas; el nodo además deduplica por
  `commandId` como defensa en profundidad.

### Requirement: Contrato documentado para integración con el backend

El contrato SHALL quedar documentado en `Desarrollo/embebido/` (topics, payloads de
telemetría/comando/ack, QoS y ejemplos) como fuente única de verdad, de modo que la
implementación pendiente del `ActionExecutor` y del publisher de comandos en el backend
(previstos en `add-rules-engine`) no requiera reinterpretar el diseño. Este cambio NO
implementa el lado backend del canal de comando; solo define el contrato y el firmware que
lo respeta.

#### Scenario: Fuente única de verdad del contrato
- **WHEN** un desarrollador implemente el canal de comando en el backend
- **THEN** encuentra en `Desarrollo/embebido/` la especificación completa (topics,
  payloads de comando y ack, QoS) que el firmware ya respeta.
