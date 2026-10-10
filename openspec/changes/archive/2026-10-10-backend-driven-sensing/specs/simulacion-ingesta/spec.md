## ADDED Requirements

### Requirement: Simulated nodes answer LEER_AHORA

The simulator SHALL behave like a real node under pure pull: it SHALL subscribe to
`nursery/zone/+/command` and, on each `LEER_AHORA` for a zone it simulates, publish exactly one
telemetry round for that zone on `nursery/zone/{zonaId}/telemetry`. It SHALL also publish one round
per simulated zone when automatic emission starts (the simulated "boot read"). It SHALL NOT publish
on its own timer, and SHALL NOT use `intervaloSensadoMinutos` (or any interval) to pace emission.
While automatic emission is off it SHALL ignore `LEER_AHORA`, so manually loaded values are not
overwritten.

#### Scenario: Pull answered
- **GIVEN** automatic emission is on and the simulator simulates `MZ-1`
- **WHEN** `LEER_AHORA` arrives on `nursery/zone/MZ-1/command`
- **THEN** exactly one telemetry round is published on `nursery/zone/MZ-1/telemetry`

#### Scenario: Unknown zone ignored
- **GIVEN** automatic emission is on and `MZ-9` is not simulated
- **WHEN** `LEER_AHORA` arrives for `MZ-9`
- **THEN** nothing is published

#### Scenario: Boot round
- **GIVEN** the simulator simulates `MZ-1` and `MZ-2`
- **WHEN** automatic emission is turned on
- **THEN** one round is published per zone, and nothing more until a `LEER_AHORA` arrives

#### Scenario: No autonomous push
- **GIVEN** automatic emission is on and no command arrives
- **WHEN** 10 minutes pass
- **THEN** no telemetry is published after the boot round

#### Scenario: Emission off
- **GIVEN** automatic emission is off
- **WHEN** `LEER_AHORA` arrives for a simulated zone
- **THEN** nothing is published

#### Scenario: Malformed command
- **WHEN** a non-JSON payload or an unknown `accion` arrives on a zone command topic
- **THEN** it is logged and ignored without stopping the simulator
