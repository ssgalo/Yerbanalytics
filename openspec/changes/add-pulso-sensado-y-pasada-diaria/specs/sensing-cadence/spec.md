## MODIFIED Requirements

### Requirement: Backend-driven periodic pull

The backend SHALL be the single owner of the sensing cadence. At every sensing pulse (see `pulso-sensado`) it SHALL
publish one `LEER_AHORA` command (`nursery/zone/{zonaId}/command`, QoS 1, no retain, existing contract payload) to
EVERY zone in the current topology. Pulses start at slots of `intervaloSensadoMinutos` anchored at 02:00 (vivero
time zone), and can also be requested on demand. There SHALL be no autonomous fallback: if the backend does not
pull, no periodic data is produced.
(Previously: one pull every `sensingPullIntervalSeconds` seconds, counted from the previous completion.)

#### Scenario: One pull per zone per pulse
- **GIVEN** a topology with zones `MZ-1` and `MZ-2`
- **WHEN** a pulse starts
- **THEN** exactly one `LEER_AHORA` is published to `nursery/zone/MZ-1/command` and one to `nursery/zone/MZ-2/command`

#### Scenario: Cadence follows the slots
- **GIVEN** `intervaloSensadoMinutos` = 240
- **WHEN** the clock goes from 02:00 to 06:00
- **THEN** a pulse starts at 02:00 and at 06:00, and none in between

#### Scenario: Empty topology
- **GIVEN** a topology with no zones
- **WHEN** a pulse starts
- **THEN** nothing is published, the pulse ends at once and the next one is still scheduled

### Requirement: Interval changes apply on the next tick

The backend SHALL re-read `intervaloSensadoMinutos` from the persisted configuration when computing every next
pulse, so a saved change takes effect without a restart or a reflash. Pulses SHALL NOT overlap: a scheduled pulse
that finds one in progress is skipped.
(Previously: X was `sensingPullIntervalSeconds`, clamped to [10, 3600] s.)

#### Scenario: Interval shortened at runtime
- **GIVEN** `intervaloSensadoMinutos` = 240 and the last pulse at 10:00
- **WHEN** 120 is saved at 10:30
- **THEN** the next pulse starts at 12:00

#### Scenario: Out-of-range stored value
- **GIVEN** a stored value outside [60, 360] (e.g. 30 or 600)
- **WHEN** the next pulse is computed
- **THEN** the clamped value (60 or 360) is used

### Requirement: Single effective stale threshold

The backend SHALL resolve the effective stale threshold in exactly one place as
`max(floor, intervalo del pulso + ventana de respuesta)`, where `floor` is the catalog parameter
`seguridad.antiguedad-max-lectura`, the pulse interval is `intervaloSensadoMinutos` (clamped) and the response
window is `reintento-seg × intentos`. The threshold protects irrigation (a reading or a substrate humidity older
than it SHALL NOT be used to irrigate); it SHALL NOT decide whether a node is out of service, which follows the
consecutive-missed-pulses escalation (see `pulso-sensado`). Every consumer (stale-sensor rule, irrigation dispatch
revalidation, reading freshness, frontend display) SHALL use this resolved value; none SHALL hold its own constant.
(Previously: `max(floor, 3 × sensingPullIntervalSeconds)`, and it also drove the offline status of the nursery.)

#### Scenario: Default interval
- **GIVEN** floor = 90 s, a 240 min pulse and a 180 s response window
- **THEN** the effective threshold is 14 580 s

#### Scenario: Floor dominates
- **GIVEN** a floor larger than the pulse interval plus the window
- **THEN** the effective threshold is the floor

#### Scenario: One missed pulse
- **GIVEN** a 240 min pulse and the last `MZ-1` reading at 06:00
- **WHEN** `MZ-1` does not answer the 10:00 pulse
- **THEN** after 10:03 its reading is stale for irrigation, while the nursery keeps showing the zone with its last reading (first warning, not out of service)

### Requirement: On-demand reads coexist with the periodic pull

The on-demand `LEER_AHORA` and the `LECTURA` sequence SHALL keep working unchanged. A `LECTURA` sequence SHALL accept
as its reply any telemetry of its zone received at or after its `pedidoEn`, whether it was triggered by its own
request or by a pulse, and SHALL NOT accept telemetry received before `pedidoEn`. Telemetry that does not answer a
pulse SHALL update the zone reading but SHALL NOT trigger the rule engine (see `pulso-sensado`).

#### Scenario: Pulse reply satisfies the sequence
- **GIVEN** a `LECTURA` sequence for `MZ-1` with `pedidoEn` = T
- **WHEN** a pulse-triggered telemetry from `MZ-1` is received at T + 1 s
- **THEN** the sequence shows those values and ends `COMPLETADA`

#### Scenario: Earlier telemetry rejected
- **GIVEN** a `LECTURA` sequence for `MZ-1` with `pedidoEn` = T
- **WHEN** the only `MZ-1` telemetry was received at T − 1 s
- **THEN** it is not taken as the reply

#### Scenario: Sequence outside a pulse does not run the engine
- **GIVEN** no pulse in progress
- **WHEN** a `LECTURA` sequence for `MZ-1` receives its telemetry
- **THEN** the sequence completes with the values and the engine is not evaluated
