## ADDED Requirements

### Requirement: Backend-driven periodic pull

The backend SHALL be the single owner of the sensing cadence. Every X seconds it SHALL publish
one `LEER_AHORA` command (`nursery/zone/{zonaId}/command`, QoS 1, no retain, existing contract
payload) to EVERY zone in the current topology. X is `sensingPullIntervalSeconds` from the
persisted operating configuration. There SHALL be no autonomous fallback: if the backend does not
pull, no periodic data is produced.

#### Scenario: One pull per zone per tick
- **GIVEN** a topology with zones `MZ-1` and `MZ-2` and X = 60
- **WHEN** a tick fires
- **THEN** exactly one `LEER_AHORA` is published to `nursery/zone/MZ-1/command` and one to `nursery/zone/MZ-2/command`

#### Scenario: Cadence follows X
- **GIVEN** X = 60 and the previous pull cycle completed at T
- **WHEN** the clock reaches T + 60 s
- **THEN** the next pull cycle starts, and no pull is published between T and T + 60 s

#### Scenario: Empty topology
- **GIVEN** a topology with no zones
- **WHEN** a tick fires
- **THEN** nothing is published and the scheduler keeps ticking

### Requirement: Interval changes apply on the next tick

The backend SHALL re-read X from the persisted configuration when computing every next tick, so a
saved change takes effect without a restart or a reflash. Pull cycles SHALL NOT overlap.

#### Scenario: Interval shortened at runtime
- **GIVEN** X = 60 and the last cycle completed at T
- **WHEN** X is saved as 30 before T + 60 s
- **THEN** the cycle at T + 60 s runs, and the following one runs at T + 90 s

#### Scenario: Out-of-range stored value
- **GIVEN** a stored X outside [10, 3600] (e.g. 2 or 99999)
- **WHEN** the next tick is computed
- **THEN** the clamped value (10 or 3600) is used

### Requirement: Per-zone failure isolation

A failure to publish to one zone SHALL NOT prevent publishing to the remaining zones of the same
cycle, and SHALL NOT stop future cycles. Missed pulls SHALL NOT be queued or replayed.

#### Scenario: One zone publish fails
- **GIVEN** zones `MZ-1`, `MZ-2`, `MZ-3` and publishing to `MZ-2` throws
- **WHEN** a tick fires
- **THEN** `MZ-1` and `MZ-3` receive `LEER_AHORA`, the failure is logged, and the next tick still fires

#### Scenario: Broker unavailable
- **GIVEN** the MQTT broker is down
- **WHEN** a tick fires
- **THEN** the cycle logs the failure, publishes nothing, and the next tick retries normally

### Requirement: Single effective stale threshold

The backend SHALL resolve the effective stale threshold in exactly one place as
`max(floor, 3 × X)`, where `floor` is the catalog parameter `seguridad.antiguedad-max-lectura`.
Every consumer (stale-sensor rule, nursery status, reading freshness, frontend display) SHALL use
this resolved value; none SHALL hold its own constant.

#### Scenario: Default interval
- **GIVEN** floor = 90 s and X = 60
- **THEN** the effective threshold is 180 s

#### Scenario: Floor dominates
- **GIVEN** floor = 90 s and X = 10
- **THEN** the effective threshold is 90 s

#### Scenario: Consumers agree
- **GIVEN** X changes from 60 to 120 with floor = 90 s
- **WHEN** the rule engine, the nursery status and the frontend evaluate the same reading
- **THEN** all of them use 360 s

### Requirement: On-demand reads coexist with the periodic pull

The on-demand `LEER_AHORA` and the `LECTURA` sequence SHALL keep working unchanged. A `LECTURA`
sequence SHALL accept as its reply any telemetry of its zone received at or after its `pedidoEn`,
whether it was triggered by its own request or by a periodic pull, and SHALL NOT accept telemetry
received before `pedidoEn`.

#### Scenario: Pull reply satisfies the sequence
- **GIVEN** a `LECTURA` sequence for `MZ-1` with `pedidoEn` = T
- **WHEN** a pull-triggered telemetry from `MZ-1` is received at T + 1 s
- **THEN** the sequence shows those values and ends `COMPLETADA`

#### Scenario: Earlier telemetry rejected
- **GIVEN** a `LECTURA` sequence for `MZ-1` with `pedidoEn` = T
- **WHEN** the only `MZ-1` telemetry was received at T − 1 s
- **THEN** it is not taken as the reply
