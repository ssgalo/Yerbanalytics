# simulacion-ingesta

## Purpose

Ingesta de la telemetria simulada por el mismo pipeline que el hardware.

## Requirements

### Requirement: Modo de operación en runtime, persistido
El backend SHALL mantener un modo de operación conmutable en runtime con dos valores
—datos estáticos (`estatico`) y simulación (`simulacion`)—, SHALL exponerlo y permitir
cambiarlo sin reiniciar la aplicación, y SHALL **persistirlo en base de datos** para que
sobreviva al reinicio del backend. El valor por defecto de fábrica SHALL ser `estatico`.

#### Scenario: Consulta del modo
- **WHEN** se invoca `GET /api/simulacion`
- **THEN** el backend devuelve el modo actual y si el simulador automático está activo

#### Scenario: Cambio de modo
- **WHEN** se invoca `PUT /api/simulacion` con un modo válido
- **THEN** el backend adopta ese modo, lo persiste y lo refleja en la siguiente consulta
- **AND** un modo inválido se rechaza con un error de solicitud sin cambiar el estado

#### Scenario: El modo sobrevive al reinicio del backend
- **WHEN** se cambia el modo a simulación y luego se reinicia el backend
- **THEN** la consulta del modo devuelve simulación (no vuelve a estático)

### Requirement: Envío manual de telemetría por MQTT
El backend SHALL aceptar una lectura manual de un sensor y publicarla en el topic MQTT
real de su macro-zona (`nursery/zone/{zonaId}/telemetry`), de modo que atraviese el mismo
pipeline de ingesta que el hardware físico (adapter → receptor → actualización de
sectores). SHALL aceptar la lectura con **una, algunas o todas** las métricas (al menos
una), y la ingesta NO SHALL sobrescribir las métricas ausentes: SHALL conservar su último
valor y recomputar el estado del sector con los valores combinados. Sólo SHALL aceptar el
envío cuando el modo es simulación.

#### Scenario: Envío completo en modo simulación
- **WHEN** el modo es simulación y se invoca `POST /api/simulacion/telemetria` con el
  serial y la macro-zona de un sensor y las cinco métricas
- **THEN** el backend publica el payload en el topic de esa macro-zona y la telemetría
  actualiza los sectores de la zona

#### Scenario: Envío de una sola métrica
- **WHEN** el modo es simulación y se envía la lectura con una sola métrica (p. ej. sólo
  radiación/UV)
- **THEN** el backend publica y la ingesta actualiza sólo esa métrica en los sectores de la
  zona, conservando el último valor de las demás, y recomputa el estado del sector

#### Scenario: Envío sin ninguna métrica
- **WHEN** se envía una lectura sin ninguna métrica presente
- **THEN** el backend rechaza el envío con un error de solicitud y no publica nada

#### Scenario: Envío rechazado en modo estático
- **WHEN** el modo es estático y se invoca `POST /api/simulacion/telemetria`
- **THEN** el backend rechaza el envío con un conflicto y no publica nada

### Requirement: Fecha y hora opcional del envío
El backend SHALL permitir enviar la lectura con una fecha/hora explícita o sin ella; si no
se provee, SHALL sellar la lectura con la fecha/hora actual del servidor.

#### Scenario: Envío sin fecha/hora
- **WHEN** se envía una lectura sin `timestamp`
- **THEN** el backend publica el payload con la fecha/hora actual

#### Scenario: Envío con fecha/hora explícita
- **WHEN** se envía una lectura con un `timestamp` explícito
- **THEN** el backend publica el payload conservando ese `timestamp`

### Requirement: Gating del simulador automático
El backend SHALL ejecutar el simulador automático de telemetría únicamente cuando el modo
es simulación y el simulador automático está explícitamente activo, y SHALL mantenerlo
apagado por defecto para no sobrescribir los valores enviados manualmente ni los datos
estáticos. El on/off del simulador automático es efímero (no se persiste).

#### Scenario: Simulador en reposo por defecto
- **WHEN** la aplicación arranca sin activar el simulador automático
- **THEN** el simulador no publica telemetría y los sectores conservan los datos sembrados

#### Scenario: Simulador automático activado
- **WHEN** el modo es simulación y se activa el simulador automático
- **THEN** el simulador vuelve a publicar telemetría periódica a las macro-zonas

### Requirement: Gestión de sensores simulados persistidos
El backend SHALL mantener una lista de sensores simulados —cada uno con serial/MAC y
macro-zona— gestionable en runtime (alta, baja y listado) y **persistida en base de datos**
(sobrevive al reinicio del backend), **independiente del registro de hardware**: dar de
alta un sensor simulado NO SHALL crear ni modificar un dispositivo del registro de
hardware. El alta SHALL rechazar un serial/MAC ya usado por otro sensor simulado
(comparación normalizada, sin distinción de mayúsculas), y el listado SHALL conservar el
orden de alta.

#### Scenario: Alta y listado de un sensor simulado
- **WHEN** se da de alta un sensor simulado con serial/MAC y macro-zona
- **THEN** el sensor queda en la lista de sensores simulados y aparece en su listado
- **AND** el registro de hardware no cambia

#### Scenario: Los sensores simulados sobreviven al reinicio
- **WHEN** se dan de alta sensores simulados y luego se reinicia el backend
- **THEN** el listado devuelve los mismos sensores, en el mismo orden de alta

#### Scenario: Serial/MAC duplicado
- **WHEN** se da de alta un sensor simulado con un serial/MAC que ya existe entre los
  simulados
- **THEN** el backend rechaza el alta con un error de solicitud

#### Scenario: Baja de un sensor simulado
- **WHEN** se elimina un sensor simulado
- **THEN** deja de aparecer en el listado y no se puede enviar desde él, incluso tras
  reiniciar el backend

### Requirement: Heartbeat del nodo por coincidencia de serial/MAC
Al ingerir una lectura, el backend SHALL actualizar el estado técnico (heartbeat:
batería/señal/último-update) del dispositivo registrado **cuyo serial/MAC coincide** con el
de la telemetría. Si ningún dispositivo registrado tiene ese serial/MAC, NO SHALL
actualizar el heartbeat de ningún nodo. La actualización de los sectores de la macro-zona
SHALL ocurrir por `zonaId`, con independencia de que exista un dispositivo registrado.

#### Scenario: Hardware registrado con el mismo serial/MAC
- **WHEN** se envía una lectura de un sensor simulado y existe un dispositivo registrado con
  el mismo serial/MAC en esa macro-zona
- **THEN** ese dispositivo actualiza su heartbeat y los sectores de la macro-zona reflejan
  la lectura

#### Scenario: Sin hardware registrado con ese serial/MAC
- **WHEN** se envía una lectura cuyo serial/MAC no coincide con ningún dispositivo
  registrado
- **THEN** los sectores de la macro-zona reflejan la lectura, pero ningún nodo actualiza su
  heartbeat

### Requirement: Regeneración de topología robusta bajo concurrencia
La regeneración de la topología SHALL reemplazar la grilla existente con un borrado en
bloque (una sentencia por tabla), de modo que sea rápido y no quede bloqueado ni falle por
la actividad concurrente de lectura o de los procesos en segundo plano (p. ej. la
evaluación de historial). La regeneración SHALL dejar cada sector sin valores históricos
(offline, lecturas en `null`).

#### Scenario: Regenerar mientras el vivero se consulta
- **WHEN** se regenera la topología mientras el panel general consulta el vivero de forma
  periódica
- **THEN** la regeneración responde sin quedar colgada ni fallar por bloqueo/optimistic
  locking, y los sectores quedan offline sin lecturas previas
