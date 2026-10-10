## ADDED Requirements

### Requirement: Sensing pull interval editor

The configuration view SHALL let the user edit the sensing pull interval (seconds) in the
intervals block, labelled so it cannot be confused with the irrigation read cycle
(`intervaloSensadoMinutos`). Next to the field it SHALL show a hint with the effective stale
threshold that the edited value would produce (`max(floor, 3 × X)`). The value SHALL be saved
through `DataRepository.saveConfig()` together with the rest of the configuration, in both mock and
http modes.

#### Scenario: Current value loaded
- **GIVEN** the saved interval is 60 s
- **WHEN** the user opens `/configuracion`
- **THEN** the field shows 60 and the hint shows a 180 s threshold (floor 90 s)

#### Scenario: Hint reflects the backend-computed threshold after saving
- **GIVEN** floor = 90 s and a stored interval of 60 s (hint shows 180 s)
- **WHEN** the user types 120
- **THEN** the hint keeps showing 180 s, labelled as recomputed on save
- **WHEN** the user saves
- **THEN** the hint shows the `effectiveStaleThresholdSeconds` returned by the backend (360 s); the browser never computes the formula

#### Scenario: Out-of-range value blocks saving
- **WHEN** the user enters 5 or 4000
- **THEN** the field is marked invalid and Save is disabled

#### Scenario: Saved value sent
- **WHEN** the user saves a valid interval of 30
- **THEN** `saveConfig()` receives `sensingPullIntervalSeconds: 30`

#### Scenario: Factory reset
- **WHEN** the user restores factory values
- **THEN** the field returns to 60
