## MODIFIED Requirements

### Requirement: Trigger reactivo y Watchdog proactivo

The engine SHALL be evaluated through two complementary paths:

- **Reactive**: triggered by every incoming MQTT telemetry packet (whether it answers a periodic
  pull, an on-demand `LEER_AHORA` or a boot read).
- **Proactive (Watchdog)**: a periodic scheduler that iterates all sectors to detect down nodes
  (no recent telemetry) through `StaleSensorRule`, and to evaluate rules that do not depend on
  fresh telemetry (such as the `MediasombraRule` day plan).

The silence threshold used by `StaleSensorRule` SHALL be the effective stale threshold
`max(floor, 3 × X)` resolved once (see `sensing-cadence`), never a rule-local constant.
(Previously: the silence threshold was a fixed `stale-threshold-ms` / `seguridad.antiguedad-max-lectura` value sized for a 30 s firmware push.)

#### Scenario: Watchdog detects a silent node

- **GIVEN** floor = 90 s and X = 60 (effective threshold 180 s)
- **WHEN** a sector receives no MQTT telemetry for more than 180 s
- **THEN** the Watchdog triggers its evaluation and `StaleSensorRule` emits a disconnected-node alert

#### Scenario: One missed pull is tolerated

- **GIVEN** X = 60 and effective threshold 180 s
- **WHEN** the last telemetry is 125 s old (one pull missed)
- **THEN** `StaleSensorRule` does not flag the sector as stale

#### Scenario: Threshold follows a new interval

- **GIVEN** floor = 90 s and a 200 s old reading
- **WHEN** X changes from 60 (threshold 180 s) to 120 (threshold 360 s)
- **THEN** the next evaluation no longer flags the reading as stale

#### Scenario: Rustification stage change without telemetry

- **WHEN** the sowing-cycle day enters a new stage of the rustification plan and no recent
  telemetry has arrived
- **THEN** the Watchdog triggers the evaluation and `MediasombraRule` emits `MOVER_MEDIASOMBRA`
