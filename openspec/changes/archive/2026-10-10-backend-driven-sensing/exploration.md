# Exploration: backend-driven-sensing

## Answer first

Everything needed already exists except the cadence driver: `ComandoZonaPublisher.leerAhora` works, LEER_AHORA → telemetry round-trip is ~0.4 s, and the firmware already handles the notification. The work is:

1. A backend `SensingScheduler`.
2. Removing the firmware timer.
3. A staleness threshold derived from X.
4. The simulator answering LEER_AHORA.
5. Updating docs/specs that state "30 s push".

User decisions: **pure pull** (no autonomous firmware fallback), default X = 1 min, changeable without reflashing, all new code/docs in **English**.

## Affected areas

| Area | File | Change |
|---|---|---|
| Firmware | `Desarrollo/embebido/sensado/task_sensado.cpp:75` | `pdMS_TO_TICKS(SENSOR_POLLING_INTERVAL_MS)` → `portMAX_DELAY`; drop `SENSOR_POLLING_INTERVAL_MS` from `comun/config.example.h`; keep boot read |
| Backend (new) | `SensingScheduler` | `SchedulingConfigurer` trigger task |
| Backend config | `ConfiguracionOperativaEntity`, `ConfiguracionOperativa` DTO, `ConfiguracionService` | new interval field + clamp/validation |
| Backend staleness | `ParametrosSeguridad.java:13-16`, `StaleSensorRule`, `NurseryService.umbralAntiguedadMs` (~675), `FrescuraLectura` | derive threshold from X |
| Simulator | `Desarrollo/simulador/server/emission.ts` (~100-106) | subscribe to `nursery/zone/+/command`, publish a telemetry round on LEER_AHORA |
| Frontend | `IntervalosForm.tsx`, `configValidation.ts`, `domain.ts`, `httpRepository.ts`, `data/mock/config.ts`, `resumenVivero.ts` | interval editor field; offline logic unchanged if threshold comes from backend |
| Docs/specs | see list below | update "30 s push" statements |

## Fork 1 — where X lives

| Option | Pros | Cons |
|---|---|---|
| A. Spring property | Trivial | Needs restart — defeats "change at the expo" |
| **B. New DB field in `ConfiguracionOperativa` (recommended)** | UI-editable; `NurseryWatchdog` (84-103) already re-reads DB per trigger cycle → applies next tick, no restart | Entity/DTO/migration + frontend field |
| C. Reuse `intervaloSensadoMinutos` | No new field | Different concept (riego read cycle, default 240 min, clamp 60-360, used by `CicloLectura`); breaks the 60 min floor |

Recommendation: **B** — new field `sensingPullIntervalSeconds`, default 60, clamp ~10-3600 (seconds so expo demos can use 10 s). Leave `intervaloSensadoMinutos` untouched; confirm in design it is riego-only (simulator reads it today for push cadence — that goes away).

## Fork 2 — scheduler and zone enumeration

- `SchedulingConfigurer.addTriggerTask` like `NurseryWatchdog`; trigger computes `lastCompletion + interval` from DB each cycle.
- Enumerate zones via `zonaRepository.findAll()` (or a lighter id-only query).
- Per-zone try/catch so one failed publish doesn't stop the rest; broker down → log and continue.
- Dedicated named scheduler in `SchedulersConfig` (not the shared pool) so a slow watchdog sweep can't delay pulls.
- Inject `Clock` (see `RelojInyectadoTest` convention).

## Staleness derived from X

Today `seguridad.antiguedad-max-lectura` = 90 s factory ("3 publication intervals of 30 s"). With X = 60 s that allows one missed pull; with X ≥ 90 s everything goes stale.

Recommendation: effective threshold = `max(configured floor, 3 × X)`, resolved in ONE place (`ParametrosSeguridad` / `CatalogoParametrosService`). `StaleSensorRule`, `NurseryService.umbralAntiguedadMs`, `FrescuraLectura` and frontend offline logic all consume the resolved value. Verify how the frontend gets the threshold (`resumenVivero.ts`); if hardcoded, expose it from backend.

## Liveness without ack

- No commandId/ack in the LEER_AHORA response → staleness is the liveness signal (existing `StaleSensorRule`). No new mechanism.
- **Coupling to check in design:** `SecuenciaService` LECTURA correlates "first telemetry after `pedidoEn`" (`LecturaZonaReceiver`). A periodic pull's telemetry could be consumed as the sequence's reply.
- Ack protocol is out of scope (firmware contract change).

## Firmware under pure pull

- Keep boot read (immediate first reading).
- Then `ulTaskNotifyTake(pdTRUE, portMAX_DELAY)`; each LEER_AHORA → `pedirLecturaAhora` → `xTaskNotifyGive`.
- Remove `SENSOR_POLLING_INTERVAL_MS` and its docs.
- `buffer_offline` (NVS FIFO, cap 50) becomes mostly irrelevant (requests can't arrive while MQTT is down) but harmless — keep it, note in proposal.
- No autonomous fallback (user decision): node stays silent until next request.

## MQTT session / QoS

- Backend publishes commands QoS 1 (`MqttConfig.java:194`).
- Whether commands queue for an offline node depends on ESP32 persistent session + fixed client id — **unverified** (client id has a random-ish suffix today).
- Burst of late LEER_AHORA collapses into one read (`ulTaskNotifyTake(pdTRUE, …)` clears the count) — acceptable.
- Confirm retain=false for command topic.
- A missed request = one skipped sample; next tick recovers.

## Simulator

- Today: own `setInterval` + 5 s `configWatcher` reading `intervaloSensadoMinutos`; no command subscription.
- Change: subscribe to `nursery/zone/+/command`; on LEER_AHORA publish one telemetry round for that zone; keep boot emission; remove auto-push timer (prefer removal over a legacy toggle).
- Tests: `npm test` (strict TDD).

## Frontend

- New field in `IntervalosForm.tsx` (~89) + `configValidation.ts`, `domain.ts`, `data/mock/config.ts`, `httpRepository.ts`. English identifiers.
- Hint showing effective stale threshold ("No signal after 3 × interval").
- Tests: vitest.

## Backend tests (strict TDD)

- `SensingScheduler`: injected Clock, zone loop, per-zone failure isolation, DB-driven interval change.
- `ConfiguracionService`: clamp/validation.
- Staleness derivation: `CatalogoParametrosServiceTest` + stale rule tests.
- Contract: `ConfiguracionControllerContratoTest` for the new field.
- Firmware: compile-only via arduino-cli.

## Docs/specs to update

- `openspec/changes/add-firmware-esp32` (specs/firmware-sensado/spec.md:6, design, tasks)
- `openspec/changes/fix-integracion-nodo-real/proposal.md`
- `openspec/changes/implement-reglas-riego/design.md`
- `docs-motor-reglas-e-integracion/circuito-sensado-a-motor.md`, `conectar-esp32.md`
- `Desarrollo/embebido/README.md`
- `reglas_v2.md:754` already describes pull — no change.

## Risks

- Staleness mismatch if only some consumers are updated.
- `SecuenciaService` LECTURA may match a periodic-pull telemetry (false early match).
- Firmware MQTT persistent session / retain unverified.
- No fallback: backend outage = zero data (accepted by user).
- Deploy order: new backend + old firmware → double publishing (harmless); new firmware + old backend → no data after boot.
- `intervaloSensadoMinutos` naming confusion in the UI — new label must be unambiguous.

Effort: medium, ~8-12 tasks. Docs edits may push past the 400-line budget — check in `sdd-tasks`.
