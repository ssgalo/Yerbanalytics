## MODIFIED Requirements

### Requirement: Materialización de acciones con efectos reales

El `ActionExecutor` SHALL ser el único punto donde las acciones emitidas por el motor se convierten
en efectos, con una excepción explícita para el riego: `ACTIVAR_VALVULA` SHALL **encolar** una
solicitud tipada (volumen, duración, humedad, regla) en la cola de riego de la macro-zona, y el
`DespachoRiego` SHALL ser quien publique el comando y registre el riego (ver `reglas-riego`). La
duración SHALL viajar tipada en la acción, no embebida en el texto del motivo. El executor SHALL
seguir registrando un `NOOP_INFO` (Registro de Inacción) cuando el motor decide no actuar, y SHALL
persistir cada acción `ALERTA` como evento "Alerta" una sola vez por macro-zona, regla y ciclo de
lectura.

#### Scenario: Riego encolado
- **WHEN** `RiegoPorDeficitRule` emite `ACTIVAR_VALVULA` en una evaluación de telemetría
- **THEN** el `ActionExecutor` encola la solicitud y no publica comando ni registra historial en ese
  momento

#### Scenario: Decisión que retira de la cola
- **WHEN** una evaluación de telemetría de un sector en cola no emite `ACTIVAR_VALVULA`
- **THEN** la solicitud del sector sale de la cola

#### Scenario: El barrido no toca la cola
- **WHEN** el Watchdog evalúa un sector en cola sin métricas
- **THEN** la solicitud sigue en la cola

#### Scenario: Registro de inacción al posponer riego
- **WHEN** `PosponerPorLluviaRule` emite `POSTPONE_RIEGO`
- **THEN** el `ActionExecutor` persiste un evento de inacción con el motivo, sin publicar comando

### Requirement: Idempotencia mediante cooldowns

El motor SHALL evitar re-emitir el mismo comando de **riego** a un sector mediante el ciclo de
lectura (a lo sumo un riego por sector y ciclo, y ninguno con un riego en curso), en lugar de un
cooldown por tiempo. Para los demás actuadores sigue vigente el cooldown
`ACTION_COOLDOWN_MINUTES`, todavía sin implementar.

#### Scenario: Riego bloqueado por ciclo de lectura
- **WHEN** el sector ya recibió un riego en el ciclo de lectura actual
- **THEN** `CicloLecturaRiegoRule` emite `ABORT_RIEGO` con la hora del riego y el inicio del ciclo
