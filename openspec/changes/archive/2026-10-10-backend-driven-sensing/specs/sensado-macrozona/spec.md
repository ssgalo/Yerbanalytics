## MODIFIED Requirements

### Requirement: Estado del nodo testigo

The panel SHALL show the status of the node that produced the reading: battery level, signal
quality and age of the last reading. It SHALL visibly flag a low battery and a node that has been
silent for too long. "Too long" SHALL be the effective stale threshold `max(floor, 3 × X)`
provided by the backend (see `sensing-cadence`); the panel SHALL NOT hardcode its own value. Nodes
report only at boot and when asked (`LEER_AHORA`), so the expected reading age is about X.
(Previously: the silence threshold was implicit and sized for a 30 s autonomous firmware push.)

#### Scenario: Node operating

- **GIVEN** an effective threshold of 180 s
- **WHEN** the node's last reading is 60 s old
- **THEN** battery, signal and the elapsed time since the last reading are shown, with no stale warning

#### Scenario: Node silent

- **GIVEN** an effective threshold of 180 s
- **WHEN** the node's last reading is 181 s old
- **THEN** the panel marks the reading as outdated and warns that the values are not current

#### Scenario: Threshold changes with the interval

- **GIVEN** a 200 s old reading
- **WHEN** the backend-reported threshold changes from 180 s to 360 s
- **THEN** the panel stops marking the reading as outdated

#### Scenario: Low battery

- **WHEN** the node's battery level falls below the defined threshold
- **THEN** the panel flags it visually
