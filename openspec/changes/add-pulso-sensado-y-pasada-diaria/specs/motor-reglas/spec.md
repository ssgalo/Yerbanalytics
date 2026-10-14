## ADDED Requirements

### Requirement: El motor sólo se dispara en el pulso

The engine SHALL be evaluated only when a zone answers a sensing pulse that runs the engine (scheduled, or manual
with `ejecutarMotor: true`, see `pulso-sensado`): every rule runs for the sectors of that zone, including
`ManualLockRule`, `StaleSensorRule` and the shading rule. There SHALL be no periodic sweep of the engine between
pulses. Telemetry that does not answer such a pulse SHALL NOT trigger the engine. The rustification plan (fixed
shading schedule) is not part of the engine and SHALL NOT depend on a pulse.

The silence threshold used by `StaleSensorRule` SHALL be the effective stale threshold resolved once (see
`sensing-cadence`), never a rule-local constant.

#### Scenario: Pulso con motor
- **WHEN** `MZ-1` answers a scheduled pulse
- **THEN** every rule is evaluated once for each of its sectors

#### Scenario: Sin barrido entre pulsos
- **GIVEN** pulses at 10:00 and 14:00
- **WHEN** the clock goes from 10:05 to 13:55
- **THEN** no rule is evaluated for any sector

#### Scenario: Humedad de sustrato vieja en la respuesta
- **GIVEN** a zone that answers the pulse without substrate humidity, whose last humidity is older than the effective threshold
- **WHEN** its sectors are evaluated
- **THEN** `StaleSensorRule` blocks irrigation

### Requirement: Las reglas de diagnóstico consumen diagnósticos nuevos

Una regla SHALL poder declarar que usa el diagnóstico de IA del sector. En una evaluación de pulso, esa regla SHALL
correr sólo si el sector tiene un diagnóstico nuevo: uno cuya hora de llegada es posterior a la del último
diagnóstico usado por el sector (o el sector nunca usó uno). Si no hay diagnóstico nuevo, la regla SHALL figurar en
la traza como omitida por falta de diagnóstico nuevo, sin acciones ni filas de inacción. Cuando al menos una regla
de diagnóstico se evaluó, el diagnóstico SHALL quedar marcado como usado en la base, en la misma transacción de la
evaluación (sobrevive a un reinicio; si la transacción revierte, sigue siendo nuevo). Si ninguna corrió (rama
bloqueada, bloqueo manual), SHALL seguir siendo nuevo para el pulso siguiente.

#### Scenario: Primer pulso tras la pasada
- **GIVEN** un diagnóstico de `MZ-1-001` llegado a las 11:05 y nunca usado
- **WHEN** `MZ-1` responde al pulso de las 14:00
- **THEN** la regla de dosificación se evalúa con ese diagnóstico y el diagnóstico queda marcado como usado

#### Scenario: Pulsos siguientes del día
- **GIVEN** que el diagnóstico de las 11:05 se usó en el pulso de las 14:00
- **WHEN** `MZ-1` responde al pulso de las 18:00
- **THEN** la regla de dosificación figura omitida por falta de diagnóstico nuevo y no actúa

#### Scenario: Bloqueo manual
- **GIVEN** un diagnóstico nuevo y un bloqueo manual activo que emite `ABORT_ALL`
- **WHEN** el sector se evalúa en un pulso
- **THEN** la regla de dosificación no corre y el diagnóstico sigue nuevo para el pulso siguiente

#### Scenario: Reinicio del backend
- **GIVEN** un diagnóstico ya usado
- **WHEN** el backend se reinicia y llega el próximo pulso
- **THEN** el diagnóstico sigue figurando como usado y la regla no vuelve a actuar

## MODIFIED Requirements

### Requirement: Materialización de acciones con efectos reales

El `ActionExecutor` SHALL ser el único punto donde las acciones emitidas por el
motor se convierten en efectos físicos: publicación MQTT de comandos al actuador,
persistencia en `historial_evento` y actualización del campo de estado del sector.
El executor SHALL registrar las acciones (riego, insumo, mediasombra, alertas) y, en cada evaluación de pulso, el
Registro de Inacción completo del sector: una fila por cada `NOOP_INFO` o acción bloqueante de la evaluación, haya
cambiado o no la decisión respecto del pulso anterior. No SHALL haber deduplicación ni tope temporal: la cadencia del
pulso acota el volumen.
(Previously: el registro de inacción se escribía en cualquier evaluación sólo ante un cambio de decisión, con un tope
de `yerbanalytics.historial.inaccion-intervalo-min` por sector.)

#### Scenario: Comando MQTT al activar la válvula

- **WHEN** una regla de riego emite `ACTIVAR_VALVULA` en un pulso
- **THEN** el `ActionExecutor` encola el riego, el despacho publica el comando
  `{"actuador":"valve","accion":"ON"}` al tópico `nursery/zone/{zonaId}/sector/{sectorId}/command` y registra el
  riego en el historial

#### Scenario: Registro de inacción al posponer riego

- **WHEN** en un pulso una regla emite `POSTPONE_RIEGO`
- **THEN** el `ActionExecutor` persiste el conjunto de inacción del sector con el motivo de postergación, sin
  publicar comando al actuador

#### Scenario: Pulso con la misma decisión

- **GIVEN** que en el pulso de las 06:00 se registró la inacción del sector
- **WHEN** el pulso de las 10:00 llega a la misma decisión
- **THEN** se vuelve a registrar el conjunto de inacción completo del sector, con la hora del pulso de las 10:00

#### Scenario: No se duplica el historial en el mismo estado

- **WHEN** la válvula ya estaba en estado "Regando" y sigue en "Regando"
- **THEN** el `ActionExecutor` no genera un nuevo evento de riego en `historial_evento`

## REMOVED Requirements

### Requirement: Trigger reactivo y Watchdog proactivo
**Reason**: El motor deja de correr con cada telemetría y deja de tener un barrido periódico: sólo se dispara en el
pulso de sensado. La caída de un nodo se detecta por pulsos consecutivos sin respuesta (`pulso-sensado`), y la
mediasombra la mueven las reglas en el pulso y el plan de rustificación por horario, fuera del motor.
**Migration**: Se elimina `NurseryWatchdog` y el campo `intervaloEvaluacionMinutos` de la configuración. Ver el
requisito "El motor sólo se dispara en el pulso".
