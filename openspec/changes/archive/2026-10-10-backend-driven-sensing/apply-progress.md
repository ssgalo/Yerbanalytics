# Apply Progress: backend-driven-sensing

Mode: Strict TDD. Engram mirror: `sdd/backend-driven-sensing/apply-progress`. Nothing committed.

## TDD Cycle Evidence

Honesty note: the first apply batches recorded RED/GREEN only as prose ("RED/GREEN" in tasks.md), and verbatim
RED output was not retained. Rows below are reconstructed from that record and the existing test files; where RED
was not observed, it says so. GREEN for every row was re-executed in the verify phase (see verify-report.md).

Backend test root: `Desarrollo/backend/src/test/java/com/yerbanalytics/backend/`. Frontend: `Desarrollo/frontend/src/`.

| Task | Test file(s) | RED evidence | GREEN evidence | Refactor |
|---|---|---|---|---|
| 1.1 | `sensing/SensingCadenceTest` | Tests written first; class did not exist (compile failure). Output not retained. | 6/6 (verify run) | None |
| 1.2 | Covered by 1.3 tests and `RuleContextTestFactory` call-site fixes | Not applicable on its own: entity/DTO fields are exercised by 1.3; compile errors at DTO call sites drove the fixes. | Compiles; 1.3 tests pass | None |
| 1.3 | `service/ConfiguracionServiceIntervaloTest`, `controller/ConfiguracionControllerContratoTest` | Tests written before field/validation existed (recorded as RED/GREEN in tasks.md). Output not retained. | 25/25 and 6/6 | None |
| 1.4 | `engine/parametros/CatalogoParametrosServiceTest` | Overlay tests written before `vigentes()` overlay (recorded). Output not retained. | 36/36 | None |
| 1.5 | None (Javadoc-only) | Not applicable (documentation comments). | Backend compiles | None |
| 1.6 | `engine/StaleSensorRuleTest` | RED NOT observed: the rule already read the effective threshold via the overlay from 1.4, so the new 125 s / 200 s scenarios passed immediately. They are regression guards. | 17/17 | None |
| 1.7 | None (README) | Not applicable (docs). | n/a | None |
| 2.1 | None directly (`findAllIds` exercised via 2.3 with mocks) | Not applicable (one-line repository query). | Compiles | None |
| 2.2 | `config/SchedulersConfigTest` | Test written before the bean existed (recorded). Output not retained. | 8/8 | None |
| 2.3 | `sensing/SensingSchedulerTest` (pullAll) | Written before `SensingScheduler` existed (recorded). Output not retained. | 13/13 (with 2.4) | None |
| 2.4 | `sensing/SensingSchedulerTest` (trigger, `Clock.fixed`) | Written before the trigger existed (recorded). Output not retained. | 13/13 | None |
| 2.5 | Wiring; covered by full `./mvnw test` | Not applicable (wiring on `ApplicationReadyEvent`). | Full suite: only the 15 baseline failures, none new | Bean renamed `sensingPullScheduler` to avoid clash (see design D5) |
| 3.1 | Types/mock; checked by `tsc -b` | Not applicable (types only). | tsc clean | None |
| 3.2 | `data/mock/config.test.ts` | Tests written first (recorded). Output not retained. | 551/551 vitest | None |
| 3.3 | `features/configuracion/components/IntervalosForm.test.tsx`, `ConfiguracionPage.test.tsx` | Tests written first (recorded). Output not retained. | 551/551 | None |
| 3.4 | `data/http/httpRepository.test.ts` | Tests written first (recorded). Output not retained. | 551/551; eslint and tsc clean | `mockRepository.saveConfig` emulates backend threshold |
| 4.1 | `Desarrollo/simulador/server/contract.test.ts` | Tests written before the helpers (recorded). Output not retained. | 13/13 at verify | None |
| 4.2 | None at the time (`mqtt.ts` subscription) | Not applicable then; covered by live smoke only. | Typecheck clean | None |
| 4.3 | None at the time; now `server/emission.test.ts` (fix-up batch) | RED NOT observed originally (code written without tests, a verify WARNING). See fix-up row. | See fix-up row | None |
| 4.4 | None (removal of interval from UI/config) | Not applicable (deletions). | `npm test` and `tsc --noEmit` clean | None |
| 5.1 | None (firmware has no test harness) | Not applicable: compile-only. | Compiled OK | None |
| 5.2 | None (compile verification) | Not applicable. | arduino-cli compile OK; live ESP32 150 s: one telemetry per LEER_AHORA, no autonomous publish | None |
| 5.3 | None (docs) | Not applicable (docs). | n/a | None |
| Fix-up: 4.3 emission | `Desarrollo/simulador/server/emission.test.ts` (6 tests) | Characterization tests. RED via mutation check, each mutation applied then reverted: (M1) `if (!active) return` inverted fails "pull answered" and "ignored while off"; (M2) removing the known-zone guard fails "unknown zone ignored"; (M3) removing the boot round fails "boot round" and "fallback"; (M4) adding a `setInterval` fails "no autonomous push / no timers"; (M5) removing the topology fallback fails "fallback to six zones". | 19/19 simulator tests (13 + 6); typecheck clean | Test seam `setDependencies({ publish, fetchTopology })` added to `emission.ts`; defaults are the real functions, behavior unchanged |
| Fix-up: firmware comments | None (comments only) | Not applicable. | arduino-cli flat-copy compile OK (952922 bytes, 30% of flash; 51276 bytes RAM, 15%) | New Spanish comments in `sensado/task_sensado.h` translated to English |
| Fix-up: docs | None | Not applicable. | n/a | design.md D5 deviations and traceability notes added |

## Fix-up batch (post-verify)

| Finding | Resolution |
|---|---|
| CRITICAL 1 (no TDD table) | This file plus engram mirror |
| WARNING 1 (emission untested) | `server/emission.test.ts`, wired into `npm test` |
| WARNING 2 (Spanish comments in `task_sensado.h`) | Translated; firmware recompiled |
| WARNING 3 (panel stale scenarios) | Skipped by decision: stale is the backend-computed flag; noted in design.md |
| WARNING 4 / SUGGESTION 1 (D5 text) | design.md updated |
| SUGGESTION 2 (live-verified firmware) | design.md traceability table |
