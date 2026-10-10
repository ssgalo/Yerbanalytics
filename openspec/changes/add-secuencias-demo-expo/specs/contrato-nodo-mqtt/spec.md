## ADDED Requirements

### Requirement: Comando de zona "leer ahora"

El contrato MQTT SHALL definir el comando de zona `nursery/zone/{zonaId}/command` (backend publica QoS
1, sin retain) con el payload `{commandId, accion: "LEER_AHORA", parametros: {}}`. El nodo testigo de
la zona SHALL responder publicando la telemetría de siempre por `nursery/zone/{zonaId}/telemetry`, sin
`commandId` y sin ACK. El contrato SHALL estar espejado en `contrato.h` (fuente de verdad),
`ContratoNodo.java`, `simulador/server/contract.ts` (sin comportamiento) y el sketch `vivero_esp32_red`.

#### Scenario: Pedido atendido
- **WHEN** el nodo de `MZ-1` recibe `LEER_AHORA` y tiene sensores configurados
- **THEN** publica una telemetría en `nursery/zone/MZ-1/telemetry` con las claves y unidades del contrato

#### Scenario: Nodo sin sensores
- **WHEN** el nodo recibe `LEER_AHORA` sin sensores configurados
- **THEN** no publica nada y lo informa por serie

### Requirement: ACK de actuadores documentado

`contrato.h` SHALL documentar el ACK `{commandId, status: "SUCCESS"|"ERROR", detalle: {tipo, ...}}`
por `nursery/zone/{zonaId}/sector/{sectorId}/ack`, publicado una vez por comando al terminar de
cumplirlo, con los valores de `detalle.tipo` vigentes más `reemplazado`.

#### Scenario: Espejos coherentes
- **WHEN** se revisan `contrato.h`, `ContratoNodo.java` y `contract.ts`
- **THEN** los tres declaran el mismo tópico de comando de zona, la misma acción y los mismos estados de ACK

### Requirement: Actuadores del firmware en red

El sketch `vivero_esp32_red` SHALL suscribirse, además del riel, al comando del sector y al de la zona
configurados en `config.h`. SHALL cumplir `valve ON {durationSec}` encendiendo el driver de la bomba
(IN3/IN4/ENB) y apagándolo solo al vencer `durationSec`, `valve OFF` apagándolo, y `shade SET` con
`targetPct` 0 o 100 moviendo el motor (IN1/IN2/ENA) hasta el final de carrera correspondiente (pines 33
y 32). SHALL publicar un ACK por comando fuera del callback MQTT, deduplicar por `commandId` y dejar la
lectura de sensores como un hueco marcado. No SHALL alterar el comportamiento del riel.

#### Scenario: Bomba con red de seguridad
- **WHEN** el nodo recibe `valve ON` con `durationSec: 25` y nunca recibe el `OFF`
- **THEN** apaga la bomba a los 25 s

#### Scenario: Cancelar un despliegue
- **WHEN** llega `shade SET 100` con otro `commandId` mientras se despliega la mediasombra
- **THEN** el nodo para, publica ACK `ERROR` `reemplazado` del anterior y enrolla hasta el final de carrera

#### Scenario: Porcentaje no binario
- **WHEN** llega `shade SET` con `targetPct: 50`
- **THEN** no mueve el motor y publica ACK `ERROR` `comando_invalido`

#### Scenario: Comando repetido
- **WHEN** llega de nuevo el `commandId` del último comando terminado
- **THEN** no repite la acción y republica su ACK
