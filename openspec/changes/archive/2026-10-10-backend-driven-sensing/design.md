# Design: Backend-driven sensing (pure pull)

## Technical Approach

A new `SensingScheduler` sends `LEER_AHORA` to every zone every X s. X is `sensingPullIntervalSeconds`, stored in `configuracion_operativa` and re-read on every trigger. The stale threshold `max(floor, 3 × X)` is computed in ONE pure function. It is overlaid into the engine's parameter snapshot, so every existing consumer gets it without code changes. Firmware and simulator report only at boot and on request.

## Architecture Decisions

| # | Topic | Choice | Rejected | Why |
|---|---|---|---|---|
| D1 | Migration | `ddl-auto=update` adds a nullable `sensing_pull_interval_seconds` column. A `null` value reads as 60. No SQL script. | Flyway (not used); `NOT NULL DEFAULT` script | Project convention (`application.properties:42`, README). Adding a nullable column is safe under `update`. |
| D2 | Validation | PUT rejects values outside 10-3600 (400). Reads clamp. DTO field is `Integer`, and `null` on PUT keeps the stored value. | Silent clamp on save | Matches the existing pattern (validate on save, clamp on use, like `CicloLectura`). `null` keeps old clients and the existing contract test valid. |
| D3 | Threshold source | `SensingCadence.staleThresholdSeconds(floor, X)`. `CatalogoParametrosService.vigentes()` returns an *effective* snapshot where `seguridad.antiguedad-max-lectura` = `max(floor, 3X)`. `catalogo()` (the editor API) still shows the raw floor. | Each consumer computes it (4 call sites, `StaleSensorRule` would lose the param key in the trace) | `StaleSensorRule`, `DespachoRiego`, `NurseryService.umbralAntiguedadMs` and `FrescuraLectura.vigente` already read that key from the snapshot. The trace records the value that was actually used. |
| D4 | Frontend threshold | Not computed client-side. Staleness already arrives as `zona.lectura.stale` (`resumenVivero.ts:28`). The hint shows the read-only DTO field `effectiveStaleThresholdSeconds`, labelled "recomputed on save". | Duplicating the formula in TS for a live preview | One source of truth. |
| D5 | Scheduler wiring | Inject `@Qualifier("sensingScheduler") TaskScheduler`. On `ApplicationReadyEvent`, call `schedule(this::pullAll, trigger)`. The trigger is the same as `NurseryWatchdog`'s (`lastCompletion` + DB interval), using an injected `Clock`. | `SchedulingConfigurer` + `registrar.setScheduler` | `ScheduledTaskRegistrar` is shared by all configurers. `setScheduler` would also move `NurseryWatchdog` off `taskScheduler`. This is the only deviation from the fixed decision, and it keeps the intent (trigger task on a dedicated lane). |
| D6 | LECTURA correlation | No change. `revisarLectura` accepts `l.recibidaEn >= p.pedidoEn` (`SecuenciaService.java:461`), and `recibidaEn` is the backend receive time (`LecturaZonaReceiver:53`). The sequence always sends its own `LEER_AHORA`. | Skip it when a pull is in flight | A pull reply that arrives after `pedidoEn` was measured about 0.4 s earlier, so it is a fresh reading. The firmware merges duplicate requests (`ulTaskNotifyTake(pdTRUE)`). Skipping would need shared in-flight state for no gain. |
| D7 | Firmware MQTT | No change. `net_mqtt.cpp:67-70` uses `connect(clientId)` with a random `esp_random()` suffix. In PubSubClient this means clean session = true. Subscriptions are redone on every reconnect, and the backend publishes QoS 1 without retain (Paho default, `MqttConfig:194`). | Persistent session with a fixed id | Commands sent while a node is offline are dropped, so there is no burst on reconnect. A missed pull costs one sample, and the next tick recovers. |
| D8 | Zone enumeration | New `ZonaRepository.findAllIds()`. Each zone is wrapped in its own try/catch. The loop does not run inside a transaction. | `findAllWithSectors()` | That would load 600 sectors just to read 6 ids. |

### D5 deviations (recorded after verify)

| Deviation | Detail |
|---|---|
| Start mechanism | The scheduler is started with `sensingScheduler.schedule(task, trigger)` from an `@EventListener(ApplicationReadyEvent)`, not via `SchedulingConfigurer`. |
| Bean name | `SensingScheduler` is declared `@Component("sensingPullScheduler")` to avoid clashing with the `TaskScheduler` bean named `sensingScheduler`. |

Neither changes any spec scenario.

## Data Flow

    sensingScheduler ──tick(X from DB)──→ SensingScheduler.pullAll()
         │  for each zoneId: ComandoZonaPublisher.leerAhora (try/catch each)
         ↓
    MQTT nursery/zone/{id}/command ──→ ESP32 / simulator ──→ telemetry
         ↓
    NurseryService (lastReadingTime) ←── StaleSensorRule / DespachoRiego / snapshot
                                             ↑ vigentes() = overlay(max(floor, 3X))
    PUT /api/configuracion ──→ ConfiguracionService ──→ catalogo.invalidar() now + afterCompletion

## Interfaces / Contracts

```java
public final class SensingCadence {            // backend/sensing/
  public static final int DEFAULT_SECONDS = 60, MIN_SECONDS = 10, MAX_SECONDS = 3600;
  public static int pullSeconds(Integer raw);  // null → 60, clamp 10..3600
  public static double staleThresholdSeconds(double floorSeconds, int pullSeconds); // max(floor, 3*pull)
}
// DTO ConfiguracionOperativa gains: Integer sensingPullIntervalSeconds, Integer effectiveStaleThresholdSeconds (read-only)
```

TS: `ConfigOperativa.sensingPullIntervalSeconds: number; effectiveStaleThresholdSeconds?: number`.
Simulator: `parseZoneCommandTopic(topic)` and `isReadNow(payload)` (pure, in `contract.ts`).

## File Changes (estimated changed lines)

| Area | File | Action | ~Lines |
|---|---|---|---|
| Backend | `sensing/SensingCadence.java` | Create | 40 |
| | `sensing/SensingScheduler.java` | Create | 90 |
| | `config/SchedulersConfig.java` | `sensingScheduler` bean | 15 |
| | `model/ConfiguracionOperativaEntity.java`, `dto/ConfiguracionOperativa.java` | Field(s) | 8 |
| | `service/ConfiguracionService.java` | Map, validate, invalidate the catalog | 30 |
| | `engine/parametros/CatalogoParametrosService.java` | Effective overlay in `Estado`, public after-commit invalidation | 30 |
| | `ParametrosSeguridad`, `FrescuraLectura`, `NurseryService`, `StaleSensorRule` | Javadoc/description only | 12 |
| | `repository/ZonaRepository.java` | `findAllIds` | 3 |
| | `README.md` | Column note | 8 |
| Backend tests | `SensingSchedulerTest` (new), `SensingCadenceTest` (new), `CatalogoParametrosServiceTest`, `ConfiguracionServiceIntervaloTest`, `ConfiguracionControllerContratoTest`, `SchedulersConfigTest`, `RuleContextTestFactory` + DTO call sites | | 300 |
| Frontend | `domain.ts`, `configValidation.ts`, `IntervalosForm.tsx` (seconds field + hint), `data/mock/config.ts`, `httpRepository.ts` + tests | Modify | 110 |
| Firmware | `sensado/task_sensado.cpp` (`portMAX_DELAY`), `task_sensado.h`, `comun/config.example.h`, `README.md` | Modify | 20 |
| Simulator | `server/emission.ts` (drop timers and `configWatcher`; activation = boot round + answer `LEER_AHORA`), `mqtt.ts` (subscribe `nursery/zone/+/command`, route), `contract.ts`, `config.ts`, `api.ts`, `src/types.ts`, `App.tsx`, `AutoEmission.tsx`, `contract.test.ts` | Modify | 170 |
| Docs | `circuito-sensado-a-motor.md`, `conectar-esp32.md`, `add-firmware-esp32` spec/design/tasks, `fix-integracion-nodo-real/proposal.md`, `implement-reglas-riego/design.md` | Modify | 80 |

**Total ≈ 900 lines → well over 400.** Suggested chain: (1) config field + threshold overlay, (2) scheduler, (3) frontend, (4) simulator, (5) firmware + docs. Backend goes first, so deploy order stays safe.

## Testing Strategy

| Layer | What | How |
|---|---|---|
| Unit | `SensingScheduler.pullAll` | Mock `ZonaRepository` and `ComandoZonaPublisher`. One zone throws or returns `publicado=false` → the other zones are still published, and nothing escapes. |
| Unit | Trigger | `nextExecution(lastCompletion)` with `Clock.fixed`. Stub config 60 → +60 s. Change to 15 → the next call returns +15 s. `null` lastCompletion uses `clock.instant()`. A config read error falls back to 60. |
| Unit | `SensingCadence` | Null/clamp edges (9 → 10, 3601 → 3600). `max(90, 3×10) = 90` and `max(90, 3×60) = 180`. |
| Unit | Overlay | `vigentes()` holds the effective value, `catalogo()` holds the raw floor, and saving the operational config changes the snapshot. |
| Integration | API | Contract test: field round-trip, `null` keeps the stored value, 5 and 4000 → 400. |
| Simulator | Pure parsers | vitest on `parseZoneCommandTopic` and `isReadNow`. |
| Firmware | Compile | `arduino-cli compile`. |

## Migration / Rollout

No script needed (D1). Ship the backend first: new backend + old firmware publishes twice, which is harmless. Rollback = revert. The leftover column is ignored.

## Open Questions

- [ ] The catalog description for `antiguedad-max-lectura` is Spanish UI text. Should we rewrite it as "floor" in English (as the language rule says) or keep it in Spanish for UI consistency?
- [ ] `ComandoZonaPublisher` logs every publish at INFO: 6 zones/min ≈ 8.6k lines/day. Should it move to DEBUG?

## Verify traceability notes

| Scenario | Evidence |
|---|---|
| firmware-sensado: burst-collapse | By design (`ulTaskNotifyTake(pdTRUE)`); live-verified: ESP32 observed for 150 s produced exactly one telemetry per LEER_AHORA (17:07:14, 17:08:14, 17:09:14). No automated test (firmware has no harness). |
| firmware-sensado: silent / pull-only | Live-verified in the same 150 s window: no autonomous publishes between requests. |
| sensado-macrozona: stale at 181 s / threshold 180 to 360 | Not re-tested in the frontend by decision: the panel renders the backend-computed `stale` flag, so the threshold logic is covered by `StaleSensorRuleTest` and `CatalogoParametrosServiceTest`. |
| simulacion-ingesta: emission scenarios | Automated in `Desarrollo/simulador/server/emission.test.ts` via the `setDependencies` seam (see apply-progress.md). |
