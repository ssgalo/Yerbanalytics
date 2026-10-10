## ADDED Requirements

### Requirement: Persisted sensing pull interval

The backend SHALL persist `sensingPullIntervalSeconds` as part of the operating configuration,
with factory default 60 and valid range [10, 3600] seconds. `GET /api/configuracion` SHALL return
it and `PUT /api/configuracion` SHALL update it, audited like any other configuration change. The
field is independent of `intervaloSensadoMinutos`, which keeps its current meaning.

#### Scenario: Default on a fresh or migrated database
- **GIVEN** a freshly seeded database, or an existing one after the migration
- **WHEN** `GET /api/configuracion` is called
- **THEN** `sensingPullIntervalSeconds` is 60

#### Scenario: Valid update
- **WHEN** `PUT /api/configuracion` is called with `sensingPullIntervalSeconds: 30`
- **THEN** the next `GET /api/configuracion` returns 30 and `intervaloSensadoMinutos` is unchanged

#### Scenario: Out-of-range value rejected
- **WHEN** `PUT /api/configuracion` is called with `sensingPullIntervalSeconds` 9 or 3601
- **THEN** the backend answers 400 naming the parameter and persists nothing

#### Scenario: Boundaries accepted
- **WHEN** `PUT /api/configuracion` is called with 10, and later with 3600
- **THEN** both are persisted

### Requirement: Effective stale threshold exposed

The backend SHALL expose the effective stale threshold (`max(floor, 3 × X)`, see
`sensing-cadence`) to the frontend, so the UI does not hardcode it.

#### Scenario: Threshold follows the saved interval
- **GIVEN** floor = 90 s
- **WHEN** `sensingPullIntervalSeconds` is saved as 120
- **THEN** the backend reports an effective stale threshold of 360 s
