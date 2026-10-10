# Proposal: Backend-driven sensing (pure pull)

## Intent

Sensing cadence lives in firmware (`SENSOR_POLLING_INTERVAL_MS`, 30 s push) and in the simulator (`setInterval`). Changing it means reflashing. The backend becomes the single owner of cadence: every X seconds it sends `LEER_AHORA` to every zone. Default X = 60 s, editable at runtime from the UI. Pure pull, with no autonomous fallback. If the backend is down there is no data, and the user accepts that.

## Scope

### In Scope
- Backend `SensingScheduler` (`SchedulingConfigurer` trigger, dedicated scheduler, injected `Clock`, per-zone failure isolation).
- New field `sensingPullIntervalSeconds` in `ConfiguracionOperativa` (entity, DTO, migration, default 60, clamp 10-3600) plus API.
- Frontend editor field (`IntervalosForm`) with a hint showing the effective stale threshold.
- Stale threshold = `max(floor, 3 × X)`, resolved in ONE place and used by `StaleSensorRule`, `NurseryService`, `FrescuraLectura` and the frontend.
- Firmware: remove the periodic timer (`portMAX_DELAY`), keep the boot read, drop `SENSOR_POLLING_INTERVAL_MS`.
- Simulator: subscribe to `nursery/zone/+/command`, answer `LEER_AHORA` with one telemetry round, remove the auto-push timer.
- Docs/spec updates for the "30 s push" statements.

### Out of Scope
- Ack/commandId protocol for `LEER_AHORA`.
- Removing `buffer_offline` (kept: it does no harm).
- Renaming or repurposing `intervaloSensadoMinutos` (riego read cycle).
- Autonomous firmware fallback.

## Capabilities

### New Capabilities
- `sensing-cadence`: the backend sends `LEER_AHORA` to all zones every X seconds, X comes from the DB, and changes apply on the next tick.

### Modified Capabilities
- `configuracion-agronomica`: adds the editable sensing pull interval and the derived stale-threshold hint.
- `configuracion-persistencia`: persists `sensingPullIntervalSeconds` with default and clamp.
- `motor-reglas`: the `StaleSensorRule` threshold is derived from X.
- `sensado-macrozona`: signal-age display uses the resolved threshold. Nodes report only on request or at boot.
- `simulacion-ingesta`: the simulator answers `LEER_AHORA` instead of pushing periodically.

## Approach

Follow exploration Fork 1-B and Fork 2. The scheduler re-reads X from the DB on each trigger, as `NurseryWatchdog` already does. The on-demand `LEER_AHORA` (LECTURA sequence) is unchanged.

**SecuenciaService LECTURA correlation (decision):** the sequence takes the first telemetry after `pedidoEn` as its reply. A periodic-pull reading that arrives after `pedidoEn` is a valid, fresh reading, so it is accepted as the reply. Design MUST confirm that correlation uses the receive/measure time ≥ `pedidoEn`. **Open question:** should a sequence skip its own `LEER_AHORA` when a pull is already in flight?

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `Desarrollo/backend/.../SensingScheduler` | New | Pull driver |
| `ConfiguracionOperativaEntity`, DTO, `ConfiguracionService`, `SchedulersConfig` | Modified | Field, clamp, scheduler bean |
| `ParametrosSeguridad`, `CatalogoParametrosService`, `StaleSensorRule`, `NurseryService`, `FrescuraLectura` | Modified | Derived threshold |
| `Desarrollo/frontend/` (`IntervalosForm.tsx`, `configValidation.ts`, `domain.ts`, `httpRepository.ts`, mock, `resumenVivero.ts`) | Modified | Editor + threshold |
| `Desarrollo/embebido/sensado/task_sensado.cpp`, `comun/config.example.h`, README | Modified | Timer removed |
| `Desarrollo/simulador/server/emission.ts` | Modified | Command-driven |
| `docs-motor-reglas-e-integracion/*`, prior change docs | Modified | Docs |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Some stale-threshold consumers left on the old 90 s value | Med | Single resolver + tests per consumer |
| LECTURA matches a reply to a pull sent before `pedidoEn` | Low | Correlation by time ≥ `pedidoEn`, confirmed in design |
| ESP32 MQTT persistent session/retain unverified | Med | A missed pull is one skipped sample. Verify retain=false |
| Backend outage = no data | Accepted | Staleness alerts reveal it |
| Label confusion with `intervaloSensadoMinutos` | Med | Clear English label + hint |

## Rollback Plan

Revert the single PR. The migration only adds a column, so the old code ignores it. Reflash the previous firmware (30 s push). **Deploy order:** ship backend + firmware together. New backend + old firmware = double publishing (harmless). New firmware + old backend = no data after boot. Backend goes first if the two cannot be deployed together.

## Dependencies

- `arduino-cli` for the firmware compile check. Single PR (`size:exception` if >400 lines).

## Success Criteria

- [ ] With X = 60 s, every zone reports about once per minute with no firmware timer.
- [ ] Changing X in the UI takes effect on the next tick with no restart or reflash.
- [ ] The stale threshold equals `max(floor, 3 × X)` everywhere.
- [ ] On-demand `LEER_AHORA` and the LECTURA sequence still work.
- [ ] The simulator emits only on boot and on `LEER_AHORA`.
- [ ] `./mvnw test`, frontend `npm test`, simulator `npm test` and the firmware compile are all green.
