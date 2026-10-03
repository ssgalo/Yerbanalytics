## ADDED Requirements

### Requirement: Contrato MQTT del riel

El backend SHALL comandar el riel publicando en `nursery/rail/command` (QoS 1, sin retain) un JSON
`{commandId, actuador:"rail", accion:"IR_A"|"HOME", parametros:{posicion?}}`, y SHALL escuchar
`nursery/rail/event`, donde el riel publica `{commandId, status:"ACEPTADO"|"LLEGO"|"ERROR", posicion,
pasos, codigo?, detalle?}`. El contrato SHALL estar espejado en `contrato.h` (fuente de verdad),
`ContratoRiel.java`, `simulador/server/contract.ts` y el sketch `vivero_esp32_red`.

#### Scenario: Movimiento exitoso
- **WHEN** el backend publica `IR_A` con `posicion: 1`
- **THEN** el riel publica `ACEPTADO` con ese `commandId` antes de moverse y `LLEGO` con `posicion: 1` al llegar

#### Scenario: Comando duplicado
- **WHEN** el riel recibe un `commandId` igual al del último comando terminado
- **THEN** no se mueve y republica el evento final de ese comando

#### Scenario: Comando nuevo durante un movimiento
- **WHEN** llega un comando con otro `commandId` mientras el riel se mueve
- **THEN** el riel aborta, publica `ERROR` `REEMPLAZADO` para el anterior y ejecuta el nuevo

#### Scenario: Evento ajeno
- **WHEN** llega un evento cuyo `commandId` no es el del paso en curso
- **THEN** el backend lo descarta sin cambiar la pasada

### Requirement: Pasada del riel

El backend SHALL ejecutar, a pedido (`POST /api/pasadas`), una pasada de cinco pasos: mover a la
posición 1, capturar el primer sector de la macro-zona de menor número, mover a la posición 2,
capturar el segundo sector, volver a home. SHALL admitir una sola pasada a la vez, mantenerla en
memoria y exponer la en curso o la última por `GET /api/pasadas/actual`, con el diagnóstico de cada
captura en cuanto exista. La captura SHALL usar `CapturaService.emitirOrden` sin cambios. La pasada
SHALL NOT modificar el motor de reglas ni su flujo de telemetría.

#### Scenario: Pasada completa
- **WHEN** el riel llega a cada posición y el celular sube ambas fotos
- **THEN** la pasada termina `COMPLETADA` con los cinco pasos `OK` y dos `capturaId`

#### Scenario: Riel sin respuesta
- **WHEN** no llega ningún evento a los 5 s de publicar un comando
- **THEN** el backend republica el mismo comando una vez, y si a los 10 s sigue sin evento el paso queda `ERROR` `RIEL_SIN_RESPUESTA`, los pasos de captura pendientes `OMITIDO`, se manda el riel a home y la pasada termina `FALLIDA`

#### Scenario: Foto fallida
- **WHEN** la orden de captura de la posición 1 termina en `ERROR`
- **THEN** el paso queda `ERROR` `ORDEN_FALLIDA`, la pasada sigue con la posición 2 y termina `FALLIDA`

#### Scenario: Pasada concurrente
- **WHEN** se pide iniciar con una pasada `EN_CURSO`
- **THEN** responde 409 y no publica nada

#### Scenario: Topología insuficiente o sin celular
- **WHEN** la macro-zona de menor número tiene menos de 2 sectores, o no hay ningún dispositivo de captura con el canal abierto
- **THEN** responde 409 con el motivo y no publica nada

#### Scenario: Cancelación
- **WHEN** se cancela una pasada en curso
- **THEN** los pasos no terminados quedan `OMITIDO`, se publica `HOME` y, al terminar home, la pasada queda `CANCELADA`

#### Scenario: Diagnóstico posterior
- **WHEN** la pasada terminó y la inferencia da de alta el diagnóstico de una de sus capturas
- **THEN** `GET /api/pasadas/actual` lo muestra en el paso de esa captura

### Requirement: Firmware del riel en red

El sketch `vivero_esp32_red` SHALL ser una copia de `vivero_esp32` que conserva sus comandos por serie
y agrega WiFi y MQTT, suscribiéndose sólo a `nursery/rail/command`. SHALL atender MQTT durante los
movimientos sin reconectar mientras se mueve, SHALL conservar el chequeo de finales de carrera en
cada paso y SHALL leer credenciales y broker de un `config.h` no versionado.

#### Scenario: Keepalive durante un tramo largo
- **WHEN** el riel tarda 42 s en volver a home
- **THEN** la conexión MQTT sigue viva al terminar y el evento `LLEGO` se publica

#### Scenario: Sin conexión al terminar
- **WHEN** el movimiento termina con el broker desconectado
- **THEN** el evento final se publica al reconectar
