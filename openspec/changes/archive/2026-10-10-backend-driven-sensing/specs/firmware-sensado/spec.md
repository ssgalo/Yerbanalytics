## MODIFIED Requirements

> Depends on the unarchived `add-firmware-esp32` change, which introduces this requirement. If
> that change is archived first, this block replaces it in the main spec; otherwise apply it to
> that change's delta.

### Requirement: Muestreo periódico de sensores en tarea dedicada

The sensing subsystem SHALL sample the sensors in a dedicated FreeRTOS task that reads ONLY:
(a) once at boot, and (b) once per `LEER_AHORA` notification received on the zone command topic.
Between reads the task SHALL block indefinitely waiting for a notification. The firmware SHALL
NOT have a periodic sampling timer nor a `SENSOR_POLLING_INTERVAL_MS` constant; cadence is owned
by the backend (see `sensing-cadence`). Several notifications pending at once SHALL collapse into
a single read. There is no autonomous fallback.
(Previously: the task repeated every `SENSOR_POLLING_INTERVAL` (30 s push) using `vTaskDelay`.)

#### Scenario: Boot read
- **WHEN** the node boots and connects
- **THEN** the task reads the sensors once, builds the telemetry packet and hands it to the publish queue

#### Scenario: Read on request
- **WHEN** the node receives `LEER_AHORA` on `nursery/zone/{zonaId}/command`
- **THEN** the task performs exactly one read and publishes it

#### Scenario: Silent without requests
- **GIVEN** the boot read has been published
- **WHEN** no `LEER_AHORA` arrives for 10 minutes
- **THEN** the node publishes no telemetry

#### Scenario: Burst collapses
- **WHEN** three `LEER_AHORA` arrive while a read is in progress
- **THEN** at most one additional read is performed

#### Scenario: Interval constant removed
- **WHEN** the firmware is compiled from `config.example.h`
- **THEN** it compiles with no `SENSOR_POLLING_INTERVAL_MS` defined
