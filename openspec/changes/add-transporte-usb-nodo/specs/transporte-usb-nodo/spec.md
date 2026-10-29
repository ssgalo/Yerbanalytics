## ADDED Requirements

### Requirement: Transporte USB elegido al compilar
El firmware SHALL ofrecer un entorno de compilación `nodo_combinado_usb` que reemplaza el transporte Wi-Fi
por el transporte USB, implementando las mismas interfaces `net_wifi.h` y `net_mqtt.h`. El entorno
`nodo_combinado` SHALL seguir compilando y comportándose exactamente igual que antes de este cambio.

#### Scenario: Compilación de los dos entornos
- **WHEN** se compilan `nodo_combinado` y `nodo_combinado_usb`
- **THEN** los dos compilan sin errores, y el primero no incluye ningún archivo de `transporte_usb/`

#### Scenario: Entorno USB sin Wi-Fi
- **WHEN** arranca una placa grabada con `nodo_combinado_usb`
- **THEN** no enciende el Wi-Fi y habla con la PC por `Serial0`

### Requirement: Protocolo de líneas
La placa y el puente SHALL intercambiar una línea por mensaje con prefijo `@`: `@S <qos> <tópico>`
(suscripción) y `@P <qos> <tópico> <payload>` (publicación) de la placa a la PC; `@H <epoch> <sesión>`
(latido), `@M <tópico> <payload>` (mensaje) y `@X` (el puente perdió el broker) de la PC a la placa. Toda línea sin prefijo `@`, con un
formato inválido o más larga que el máximo SHALL descartarse sin efectos.

#### Scenario: Payload con espacios
- **WHEN** la placa publica un JSON que contiene espacios
- **THEN** el puente publica en el broker el payload completo, idéntico al original

#### Scenario: Texto ajeno en el puerto
- **WHEN** por el puerto llegan los mensajes de arranque de la ROM o una línea dañada
- **THEN** el puente la ignora y no publica nada

#### Scenario: Línea demasiado larga
- **WHEN** llega una línea más larga que el máximo permitido
- **THEN** se descarta entera, sin publicar un pedazo

### Requirement: Mismo contrato MQTT
El puente SHALL publicar en el broker los mismos tópicos y payloads que el nodo publicaría por Wi-Fi, con
la QoS que pide la placa, y SHALL entregarle a la placa los mensajes de los tópicos a los que se suscribió.
Backend, broker, dashboard, simulador y contrato MQTT SHALL NOT requerir cambios.

#### Scenario: Telemetría
- **WHEN** la placa publica la telemetría de su macro-zona por USB
- **THEN** el backend la recibe en `nursery/zone/{zona}/telemetry` como si viniera por Wi-Fi

#### Scenario: Comando a un actuador
- **WHEN** el backend publica un comando en `nursery/zone/{zona}/sector/{sector}/command`
- **THEN** la placa lo recibe por USB y responde su ACK por el mismo camino

#### Scenario: Mismo nodo para el backend
- **WHEN** la placa publica por USB
- **THEN** reporta la misma MAC que por Wi-Fi

### Requirement: Conexión por latido y recuperación automática
El puente SHALL mandar un latido `@H` cada 2 s mientras esté conectado al broker. La placa SHALL
considerarse conectada si recibió un latido en los últimos 6 s, y SHALL reenviar todas sus suscripciones
al pasar a conectada o al cambiar el id de sesión del latido. Mientras no esté conectada, publicar SHALL
fallar, para que el firmware use su buffer offline y sus reintentos.

#### Scenario: Cable desenchufado y vuelto a enchufar
- **WHEN** se desenchufa el USB y se vuelve a enchufar
- **THEN** el puente reabre el puerto, la placa reenvía sus suscripciones y la telemetría guardada se publica

#### Scenario: Puente reiniciado rápido
- **WHEN** el puente se reinicia en menos de 6 s
- **THEN** la placa reenvía las suscripciones porque cambió el id de sesión

#### Scenario: Broker caído
- **WHEN** el puente pierde la conexión con el broker
- **THEN** le manda `@X` a la placa, que pasa a desconectada en ese momento, y deja de mandar latidos

### Requirement: Hora desde el puente
Al recibir un latido con un epoch válido (≥ 1700000000) que difiere más de 2 s de su hora, la placa SHALL
ajustar su reloj a esa hora, de
modo que sus timestamps sean actuales aunque no haya NTP.

#### Scenario: Placa sin internet
- **WHEN** la placa arranca sin acceso a NTP y recibe el primer latido
- **THEN** la telemetría siguiente lleva la hora actual, no la última guardada en la flash

### Requirement: Abrir el puerto no resetea la placa
El puente SHALL abrir el puerto serie sin pasar por el estado de reset de la placa (DTR bajo con RTS alto):
baja RTS antes que DTR.

#### Scenario: Arranque del puente con la placa andando
- **WHEN** se arranca el puente con la placa ya encendida
- **THEN** la placa no se reinicia
