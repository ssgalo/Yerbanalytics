## MODIFIED Requirements

### Requirement: Materialización de acciones con efectos reales

El `ActionExecutor` SHALL ser el único punto donde las acciones emitidas por el motor se convierten
en efectos, con una excepción explícita para el riego: `ACTIVAR_VALVULA` SHALL **encolar** una
solicitud tipada (volumen, duración, humedad, regla) en la cola de riego de la macro-zona, y el
`DespachoRiego` SHALL ser quien publique el comando y registre el riego (ver `reglas-riego`). La
duración SHALL viajar tipada en la acción, no embebida en el texto del motivo. El executor SHALL
seguir registrando el Registro de Inacción (`NOOP_INFO`, `ABORT_*`, `POSTPONE_RIEGO`) cuando el motor decide no
actuar, pero **sólo cuando cambia la decisión del sector**: se recuerda por sector (y por origen,
telemetría o barrido) el tipo de acción y una clave estable del motivo (sin los números que cambian en cada
lectura) de cada regla; si la evaluación coincide no se escribe nada y si cambió la de cualquier regla se escribe
el conjunto completo del sector en esa evaluación. El barrido SHALL NOT repetir lo que la telemetría ya registró.
Tras un reinicio se registra una vez y al regenerar la topología el estado se limpia. SHALL
persistir cada acción `ALERTA` como evento "Alerta" una sola vez por macro-zona, regla y ciclo de
lectura.

#### Scenario: Riego encolado
- **WHEN** `RiegoPorDeficitRule` emite `ACTIVAR_VALVULA` en una evaluación de telemetría
- **THEN** el `ActionExecutor` encola la solicitud y no publica comando ni registra historial en ese
  momento

#### Scenario: La humedad se recuperó no retira de la cola
- **WHEN** una evaluación de telemetría de un sector en cola no emite `ACTIVAR_VALVULA` porque R-01 y R-02 ya no aplican
- **THEN** la solicitud del sector sigue en la cola (la ronda decidida se completa)

#### Scenario: Cancelación explícita de seguridad
- **WHEN** una evaluación de telemetría de un sector en cola emite un `ABORT_RIEGO` marcado como cancelación
  (R-04, S-02; R-05 y R-06 sólo para R-01) o un `ABORT_ALL` por bloqueo manual
- **THEN** la solicitud del sector sale de la cola

#### Scenario: Evaluaciones idénticas
- **WHEN** el nodo publica 60 mensajes seguidos que dan la misma decisión a un sector
- **THEN** el historial recibe una sola tanda de filas "Info" de ese sector, no 60

#### Scenario: Cambia la decisión de una regla
- **WHEN** una regla del sector pasa de `NOOP_INFO` a `ABORT_RIEGO`
- **THEN** se registra el conjunto completo de las reglas de ese sector en esa evaluación

#### Scenario: El barrido no toca la cola
- **WHEN** el Watchdog evalúa un sector en cola sin métricas
- **THEN** la solicitud sigue en la cola

#### Scenario: Registro de inacción al posponer riego
- **WHEN** `PosponerPorLluviaRule` emite `POSTPONE_RIEGO`
- **THEN** el `ActionExecutor` persiste un evento de inacción con el motivo, sin publicar comando

### Requirement: Idempotencia mediante cooldowns

El motor SHALL evitar re-emitir el mismo comando de **riego** a un sector mediante el ciclo de
lectura (a lo sumo un riego de R-01 por sector y ciclo, y ningún riego con uno en curso; R-02 sólo respeta su
tope de horas), en lugar de un cooldown por tiempo. Para los demás actuadores sigue vigente el cooldown
`ACTION_COOLDOWN_MINUTES`, todavía sin implementar.

#### Scenario: Riego bloqueado por ciclo de lectura
- **WHEN** el sector ya recibió un riego en el ciclo de lectura actual
- **THEN** `CicloLecturaRiegoRule` emite `ABORT_RIEGO` con la hora del riego y el inicio del ciclo
