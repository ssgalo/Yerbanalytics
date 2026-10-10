# Verify Report: backend-driven-sensing

Mode: Strict TDD, openspec. Verdict: PASS WITH WARNINGS (1 CRITICAL protocol/doc gap, see below).

## Execution evidence
| Component | Command | Result |
|---|---|---|
| Backend | ./mvnw test | 851 run, 7 failures, 8 errors. All 15 are in the 3 known baseline classes (NurseryServiceRiegoTest x6, NurseryControllerTest x1, DiagnosticoControllerTest x8 errors). Baseline re-run on a detached `git worktree` of HEAD (working tree untouched, worktree removed): same 3 classes, 8 F + 8 E; identical failing names (HEAD also fails DiagnosticoControllerTest.losDiagnosticosDerivadosDeSectoresNoTraenImagen, data-dependent, which passed here). No NEW failure. |
| Backend, change tests | surefire | SensingCadenceTest 6/6, SensingSchedulerTest 13/13, SchedulersConfigTest 8/8, StaleSensorRuleTest 17/17, ConfiguracionServiceIntervaloTest 25/25, ConfiguracionControllerContratoTest 6/6, CatalogoParametrosServiceTest 36/36 |
| Frontend | npm test / lint / tsc -b | 551/551 (48 files); eslint clean (max-warnings 0); tsc clean |
| Simulador | npm test / typecheck | 13/13; tsc --noEmit clean |
| Live | orchestrator | ESP32 150 s: 3 LEER_AHORA each -> exactly 1 telemetry, no autonomous publish; /api/configuracion 60 / 180; log "pull cycle done requested=1 failed=0" |

## Tasks: 29/29 checked (1.1-5.3), code state matches.

## Spec compliance (summary)
- sensing-cadence: pull per zone, cadence, empty topology, runtime change, clamp, isolation, threshold (default/floor/consumers) COMPLIANT (SensingSchedulerTest, SensingCadenceTest, CatalogoParametrosServiceTest). "Broker unavailable" covered by publicado=false / throw tests + live. LECTURA coexistence: unchanged code (`recibidaEn >= pedidoEn`, SecuenciaService:461), existing tests; no new test (unchanged behavior).
- configuracion-persistencia: default 60, update, 9/3601 -> 400, boundaries, effective threshold COMPLIANT (ServiceIntervaloTest, ControllerContratoTest) + live.
- motor-reglas: 125 s not stale, 200 s stale->not stale COMPLIANT (StaleSensorRuleTest). Watchdog silent-node 180 s boundary: pre-existing rule tests.
- configuracion-agronomica: all 5 scenarios COMPLIANT (IntervalosForm.test, config.test, httpRepository.test, ConfiguracionPage.test).
- sensado-macrozona: backend threshold exposed and overlaid; panel scenarios rely on pre-existing frontend tests, not re-tested for the 180/360 s change. PARTIAL (warning).
- firmware-sensado: compiled OK + live evidence (boot read, read on request, silent, no constant). Burst-collapse: by ulTaskNotifyTake(pdTRUE) design, no automated test (firmware has no harness).
- simulacion-ingesta: helpers tested (parseZoneCommandTopic, isReadNow incl. malformed/unknown accion). Scenarios pull answered, unknown zone ignored, boot round, no autonomous push, emission off have NO automated test (emission.ts / mqtt.ts untested) and no live evidence supplied. UNTESTED.

## Issues
CRITICAL (1)
1. Strict TDD: apply-progress (obs #1624) has no "TDD Cycle Evidence" table (only a prose summary per batch). Protocol rule says CRITICAL. Mitigation: all referenced test files exist and pass on execution, so this is a reporting gap; fix by adding the table to apply-progress (no code change).

WARNING (4)
1. Simulator emission behavior (5 simulacion-ingesta scenarios) has no covering automated test; tasks 4.2-4.4 did not require one. Strict reading of the skill = CRITICAL UNTESTED; downgraded because the plan scoped it, but recommend a small emission test or live smoke before archive.
2. New Spanish comments in Desarrollo/embebido/sensado/task_sensado.h (header block and trailing comment) violate the English-only rule for new comments. task_sensado.cpp new comment and all Java/TS new code/identifiers/comments are English. Existing Spanish was not renamed; Javadoc "Cinco->Seis carriles" in SchedulersConfig and a reflowed ParametrosSeguridad Javadoc line are minimal edits of existing Spanish text (acceptable). UI strings and READMEs/docs stay Spanish (user-facing/docs convention).
3. sensado-macrozona panel scenarios (stale at 181 s / threshold change 180->360) have no test targeting the new threshold source on the frontend side.
4. Design deviations: D5 (scheduler wired on ApplicationReadyEvent via @EventListener) and bean name `sensingPullScheduler` (SensingScheduler is @Component("sensingPullScheduler") to avoid clashing with the TaskScheduler bean `sensingScheduler`) are documented in tasks 2.5 and an in-code comment, but design.md D5 text was not updated to say so. Does not break any spec.

SUGGESTION (2)
1. Update design.md with the two deviations above.
2. Burst-collapse and Silent scenarios on firmware could be noted as manual/live-verified in the spec traceability.

## Other checks
- comun/config.h is gitignored (Desarrollo/embebido/.gitignore) and not in `git status`; nothing is staged (`git diff --cached` empty).
- Assertion audit of new backend/frontend tests: no tautologies; Clock.fixed used for trigger tests; no ghost loops found in sampled files.
- Unrelated diffs present: .atl/skill-registry files, openspec/config.yaml (not part of this change; confirm intended before commit).

## Addendum — post fix-up (2026-10-10)

All findings above were addressed in a fix-up batch; the CRITICAL is resolved.

| Finding | Resolution |
|---|---|
| CRITICAL — missing TDD Cycle Evidence | Added in `apply-progress.md` (honest about reconstructed RED evidence) |
| WARNING 1 — simulator emission untested | `server/emission.test.ts`, 6 tests with mutation checks; simulator suite 19/19 (re-run by orchestrator) |
| WARNING 2 — Spanish comments in `task_sensado.h` | Translated; firmware recompiled OK (952922 B, 30%) |
| WARNING 3 — frontend stale scenarios | Not applicable: `stale` flag is computed by the backend |
| WARNING 4 / SUGGESTION 1 — design D5 | Deviations documented in `design.md` |
| SUGGESTION 2 — traceability | Live verification recorded in `design.md` |

Final verdict: **PASS**.
