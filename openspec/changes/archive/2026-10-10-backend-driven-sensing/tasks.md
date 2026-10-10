# Tasks: Backend-driven sensing (pure pull)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~900 (design estimate) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | Single PR, 5 work-unit commits |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: size-exception
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Config field + effective threshold | PR 1 (commit 1) | Backend tests |
| 2 | SensingScheduler | PR 1 (commit 2) | Depends on 1 |
| 3 | Frontend editor | PR 1 (commit 3) | Depends on 1 (DTO) |
| 4 | Simulator answers LEER_AHORA | PR 1 (commit 4) | Independent |
| 5 | Firmware + docs | PR 1 (commit 5) | Last |

Strict TDD: every behavior task is RED → GREEN → REFACTOR in one unit. Units 3-5 can run after 1 in parallel; 2 is sequential after 1.

## Phase 1: Config field + threshold (backend)

- [x] 1.1 RED/GREEN `SensingCadenceTest` → create `sensing/SensingCadence.java` (null→60, 9→10, 3601→3600; max(90,30)=90, max(90,180)=180)
- [x] 1.2 Add nullable `sensingPullIntervalSeconds` to `ConfiguracionOperativaEntity`; add `sensingPullIntervalSeconds` + read-only `effectiveStaleThresholdSeconds` to DTO `ConfiguracionOperativa`; fix DTO call sites/`RuleContextTestFactory`
- [x] 1.3 RED/GREEN `ConfiguracionServiceIntervaloTest` + `ConfiguracionControllerContratoTest`: round-trip 30, `null` keeps stored, 9/3601 → 400 naming param, 10/3600 accepted, default 60, `intervaloSensadoMinutos` untouched
- [x] 1.4 RED/GREEN `CatalogoParametrosServiceTest`: `vigentes()` overlays `seguridad.antiguedad-max-lectura` = max(floor, 3X); `catalogo()` keeps raw floor; save invalidates (now + afterCompletion)
- [x] 1.5 Javadoc-only notes in `ParametrosSeguridad`, `FrescuraLectura`, `NurseryService`, `StaleSensorRule`; do NOT rewrite existing Spanish catalog descriptions
- [x] 1.6 RED/GREEN `StaleSensorRule` scenarios: 125 s not stale at X=60; 200 s stale→not stale when X 60→120
- [x] 1.7 `Desarrollo/backend/README.md`: nullable column note (ddl-auto, null reads as 60)

## Phase 2: SensingScheduler (backend)

- [x] 2.1 Add `ZonaRepository.findAllIds()`
- [x] 2.2 RED/GREEN `SchedulersConfigTest`: `sensingScheduler` `TaskScheduler` bean in `config/SchedulersConfig.java`; `NurseryWatchdog` stays on `taskScheduler`
- [x] 2.3 RED/GREEN `SensingSchedulerTest.pullAll`: one `leerAhora` per zone; empty topology publishes nothing; one zone throws / `publicado=false` → others published, nothing escapes; one INFO summary (requested/failed)
- [x] 2.4 RED/GREEN trigger tests with `Clock.fixed`: +60 s; config 15 → next +15 s; stored 2/99999 clamped; null lastCompletion uses clock; read error → 60
- [x] 2.5 Wire `schedule(this::pullAll, trigger)` on `ApplicationReadyEvent` (accepted D5 deviation); run `./mvnw test`

## Phase 3: Frontend

- [x] 3.1 Add `sensingPullIntervalSeconds` / `effectiveStaleThresholdSeconds?` to `ConfigOperativa` in `src/types/domain.ts`; default 60 in `data/mock/config.ts`
- [x] 3.2 RED/GREEN `configValidation.ts`: 5/4000 invalid, 10/3600 valid
- [x] 3.3 RED/GREEN `IntervalosForm.tsx`: seconds field labelled distinct from irrigation read cycle, threshold hint, Save disabled when invalid, factory reset → 60
- [x] 3.4 RED/GREEN `httpRepository.ts` maps both fields; `saveConfig()` sends 30; run `npm test`

## Phase 4: Simulator

- [x] 4.1 RED/GREEN `contract.test.ts`: `parseZoneCommandTopic`, `isReadNow` (malformed/unknown `accion` → false) in `contract.ts`
- [x] 4.2 `server/mqtt.ts`: subscribe `nursery/zone/+/command`, route to emission; log+ignore malformed
- [x] 4.3 `server/emission.ts`: remove timers + `configWatcher`; boot round on activation; answer `LEER_AHORA` only for simulated zones while on
- [x] 4.4 Drop interval from `config.ts`, `api.ts`, `src/types.ts`, `App.tsx`, `AutoEmission.tsx`; run `npm test`

## Phase 5: Firmware + docs

- [x] 5.1 `sensado/task_sensado.cpp`/`.h`: boot read, then `ulTaskNotifyTake(pdTRUE, portMAX_DELAY)`; remove `SENSOR_POLLING_INTERVAL_MS` from `comun/config.example.h`
- [x] 5.2 Compile via `Desarrollo/embebido/README.md` §2 flat-copy, FQBN `esp32:esp32:esp32s3:PSRAM=opi,FlashSize=16M,CDCOnBoot=cdc,PartitionScheme=app3M_fat9M_16MB`
- [x] 5.3 Update embebido `README.md`, `circuito-sensado-a-motor.md`, `conectar-esp32.md`, `add-firmware-esp32` spec/design/tasks, `fix-integracion-nodo-real/proposal.md`, `implement-reglas-riego/design.md` ("30 s push" → pull)

## Delivery decision

- `size:exception` approved by the user on 2026-10-10 for a single PR (~900 changed lines).
- The PR is organized as 5 work-unit commits: config + threshold → scheduler → frontend → simulator → firmware + docs.
