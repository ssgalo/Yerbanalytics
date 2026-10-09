## ADDED Requirements

### Requirement: Secuencias guionadas de actuadores

El backend SHALL ejecutar, a pedido (`POST /api/secuencias` con `{tipo, parametros}`), una de tres
secuencias de plan fijo: `RIEGO` (ABRIR `valve ON` → ESPERAR `duracionSeg` → CERRAR `valve OFF`),
`MEDIASOMBRA` (DESPLEGAR `shade SET 0` → ESPERAR `esperaSeg` → ENROLLAR `shade SET 100`) y `LECTURA`
(PEDIR "leer ahora" → ESPERAR_TELEMETRIA → MOSTRAR). SHALL publicar los comandos de actuador por el
tópico de comando del sector, sin pasar por el motor de reglas, y SHALL mantener la secuencia en
memoria, exponiendo la en curso o la última por `GET /api/secuencias/actual` (204 si no hubo
ninguna). Las secuencias SHALL NOT modificar el motor de reglas, `NurseryService` ni los flujos de
telemetría y del riel, y SHALL NOT depender del interruptor Demo Expo.

#### Scenario: Riego completo
- **WHEN** se inicia `RIEGO` con `duracionSeg: 15` y el nodo responde ACK `SUCCESS` a cada comando
- **THEN** se publica `valve ON` con `durationSec` mayor a 15, se espera 15 s, se publica `valve OFF`, y la secuencia termina `COMPLETADA` con los tres pasos `OK`

#### Scenario: Mediasombra completa
- **WHEN** se inicia `MEDIASOMBRA` y el nodo responde ACK `SUCCESS` al llegar a cada final de carrera
- **THEN** se publica `shade SET` con `targetPct: 0`, se espera `esperaSeg`, se publica `targetPct: 100`, y la secuencia termina `COMPLETADA`

#### Scenario: Lectura a pedido
- **WHEN** se inicia `LECTURA` y la zona publica una telemetría después del pedido
- **THEN** la secuencia muestra los valores de esa lectura (con `ce` en dS/m) y termina `COMPLETADA`

#### Scenario: Telemetría anterior al pedido
- **WHEN** llega una telemetría de la zona recibida antes de publicar el pedido
- **THEN** no se toma como respuesta

#### Scenario: Parámetros inválidos
- **WHEN** se pide un `tipo` desconocido o un `duracionSeg` fuera de rango
- **THEN** responde 400 con el motivo y no publica nada

### Requirement: Paso seguro y cancelación

Las secuencias de `RIEGO` y `MEDIASOMBRA` SHALL ejecutar siempre su último paso (CERRAR o ENROLLAR),
aunque un paso anterior falle o el operador cancele. Cancelar (`POST /api/secuencias/actual/cancelar`)
SHALL omitir lo pendiente, ejecutar el paso seguro y dejar la secuencia `CANCELADA` cuando éste
termine; una `LECTURA` cancelada SHALL quedar `CANCELADA` al instante.

#### Scenario: Cancelar un riego
- **WHEN** se cancela un `RIEGO` durante la espera
- **THEN** la espera queda `OMITIDO`, se publica `valve OFF` y al recibir su ACK la secuencia queda `CANCELADA`

#### Scenario: Falla al abrir
- **WHEN** el ABRIR no recibe ACK dentro de su timeout
- **THEN** queda `ERROR` `ACTUADOR_SIN_RESPUESTA`, la espera `OMITIDO`, se publica igual `valve OFF` y la secuencia termina `FALLIDA`

#### Scenario: Falla en el paso seguro
- **WHEN** el CERRAR no recibe ACK dentro de su timeout
- **THEN** la secuencia termina `FALLIDA` con un detalle que avisa que el nodo apaga la bomba solo a los `durationSec`

#### Scenario: Sin secuencia en curso
- **WHEN** se pide cancelar sin una secuencia `EN_CURSO`
- **THEN** responde 409

### Requirement: Timeouts por paso

Cada paso que espera una respuesta del hardware SHALL fallar si no la recibe a tiempo: el ACK de la
válvula a `timeout-ack-valvula-seg` (10 s), el de la mediasombra a `timeout-ack-mediasombra-seg`
(45 s) y la telemetría a `timeout-lectura-seg` (20 s), todos configurables. Un comando de actuador sin
ACK SHALL NOT republicarse.

#### Scenario: Lectura sin respuesta
- **WHEN** la zona no publica telemetría a los 20 s del pedido
- **THEN** el paso queda `ERROR` `SIN_LECTURA` y la secuencia termina `FALLIDA`

### Requirement: Un solo uso del hardware a la vez

El backend SHALL admitir una sola secuencia en curso y SHALL rechazar con 409, sin publicar nada,
iniciar una secuencia mientras corre una pasada del riel, o una pasada mientras corre una secuencia.
La exclusión SHALL derivarse del estado de cada servicio, sin estado propio que pueda quedar trabado.

#### Scenario: Secuencia con pasada en curso
- **WHEN** se pide iniciar una secuencia con una pasada `EN_CURSO`
- **THEN** responde 409 "Hay una pasada del riel en curso." y no publica nada

#### Scenario: Pasada con secuencia en curso
- **WHEN** se pide iniciar una pasada con una secuencia `EN_CURSO`
- **THEN** `POST /api/pasadas` responde 409 con el motivo y no publica nada

#### Scenario: Pedidos simultáneos
- **WHEN** llegan a la vez un pedido de pasada y uno de secuencia
- **THEN** a lo sumo uno de los dos se inicia

### Requirement: Ingesta del ACK de actuadores

El backend SHALL escuchar `nursery/zone/+/sector/+/ack` con un adaptador MQTT propio y SHALL entregar
cada ACK a las secuencias, que sólo consideran el del `commandId` del paso en curso. Un ACK ajeno (por
ejemplo, de un comando del motor) SHALL descartarse sin efecto, y un JSON ilegible SHALL loguearse sin
interrumpir la ingesta.

#### Scenario: ACK de un comando del motor
- **WHEN** llega un ACK cuyo `commandId` no es el del paso en curso
- **THEN** la secuencia no cambia

#### Scenario: ACK con error
- **WHEN** llega un ACK `ERROR` con `detalle.tipo: "falla_mecanica"` del paso en curso
- **THEN** el paso queda `ERROR` `FALLA_MECANICA` con un detalle legible
