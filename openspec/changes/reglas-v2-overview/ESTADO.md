# Estado de implementación — reglas_v2

Seguimiento de los 14 cambios del índice (`README.md`). **Se actualiza en el mismo PR que implementa cada cambio.**
El detalle de tareas de cada uno está en su `tasks.md`; acá solo el estado general.

Leyenda: ✅ implementado · 🚧 en curso · ⬜ pendiente

| Id | Cambio | Estado | Rama / PR | Depende de | Notas |
|---|---|---|---|---|---|
| **C** | `add-control-demo-reglas` | ✅ | `feat/reglas-v2-control-demo` | — | `DemoControllable`, clima simulado completo (24 marcas, UV máximo, modo offline), escenarios en el simulador. Backend: 18 tests ya fallaban en `develop` (`DiagnosticoControllerTest`, `NurseryControllerTest`) |
| **H** | `add-historial-lecturas` | ✅ | `feat/reglas-v2-historial-lecturas` | — | Tabla `zone_reading`, `ReadingHistory`, `ReadingRecorder`, `ReadingPurge`; parámetro `readings.retention-days`. Habilita 4, 7, 8 y 9 |
| 0 | `align-reglas-existentes-v2` | ✅ | `feat/reglas-v2-align` | C | S-02 completo (estado seguro, reintento a los 15 min, parámetros `sensado.*`), confianza 70 %. R-02, guarda de ciclo y escalamiento por pulso: se mantienen (D-2 a, D-3, D-6), documentado |
| 1 | `add-bloqueo-manual` | ✅ | `feat/reglas-v2-bloqueo-manual` | C | S-01. API `/api/bloqueos`, cierre inmediato de válvulas y bombas (`ActuatorShutdown`), alertas INFO, tarjeta en sector y macro-zona, escenario `LOCK` del simulador. Migración manual de permisos (`migracion-permisos-bloqueo.sql`). Pendiente en 5: que M-01/M-02 respeten el bloqueo de zona |
| 2 | `add-seguridad-lectura-hardware` | ✅ | `feat/reglas-v2-seguridad-lectura-hardware` | C | S-03 (`ReadingValidationGuard`: rango físico, relecturas, descarte por métrica, alerta WARNING→CRITICAL) y S-04 (`IncompleteHardwareRule`, `HardwareCoverage`, `ABORT_MEDIASOMBRA`). Las prioridades de las reglas se corrieron una (la nueva es la 1). Migración manual de permisos (`migracion-permisos-hardware-servicio.sql`: `hardware.ver` para el rol Servicio). Un sector sin electroválvula registrada deja de regarse solo |
| 3 | `add-fallas-esp32` | ⬜ | — | C, 2 | S-05 |
| 4 | `add-metrica-dpv` | ⬜ | — | C, H | §1.4 DPV |
| 5 | `add-mediasombra-v2` | ⬜ | — | C | M-01, M-02, §7, `ZoneRuleEngine`. Tareas 1.1/1.2 ya alineadas con lo que dejó C |
| 6 | `add-dosificacion-base` | ⬜ | — | C, 2 | §8.1–8.3. Requiere confirmar la bomba por insumo |
| 7 | `add-plan-nutricion` | ⬜ | — | C, H, 6, 5 | N-01, N-02 |
| 8 | `add-reglas-biotico` | ⬜ | — | C, H, 4, 6 | F-F1, F-F2, F-P1, F-04, F-06 |
| 9 | `add-reglas-clorosis-estres-solar` | ⬜ | — | C, H, 5, 6 | F-C1, F-C2, F-S1, F-S2, F-S3 |
| 10 | `add-efectividad-y-s06` | ⬜ | — | C, 6, 8, 9 | E-01…E-03, S-06, F-05 |
| 11 | `add-operacion-sin-internet` | ⬜ | — | C, 5, 6 | O-01…O-05 |

## Reglas de reglas_v2 por estado

| Regla / sección | Estado | Dueño |
|---|---|---|
| R-01, R-03, R-04, R-05, R-06 | ✅ ya estaban (parámetros verificados contra §11) | — |
| R-02 (tope en 0) | ⚠️ tope de 12 h siempre aplicado (desviación documentada) hasta que exista S-06; se reevalúa en 10 | 0 → 10 |
| S-01 | ✅ (mediasombra por zona: lo cierra 5, tarea 0.4) | 1 |
| S-02 | ✅ (escalamiento por pulso, no por intento: D-6) | 0 |
| S-03, S-04 | ✅ | 2 |
| S-05 | ⬜ | 3 |
| S-06 | ⬜ | 10 |
| §1.4 DPV | ⬜ | 4 |
| M-01, M-02, §7 | ⬜ | 5 |
| §8.1–8.3 | ⬜ | 6 |
| N-01, N-02 | ⬜ | 7 |
| F-F1, F-F2, F-P1, F-04, F-06 | ⬜ | 8 |
| F-C1, F-C2, F-S1, F-S2, F-S3 | ⬜ | 9 |
| E-01…E-03, F-05 | ⬜ | 10 |
| O-01…O-05 | ⬜ | 11 |

## Infraestructura transversal

| Pieza | Estado |
|---|---|
| Toda pieza de decisión es `DemoControllable` y aparece en `GET /api/demo/rules` | ✅ |
| Historial de lecturas por zona (`zone_reading`) y su servicio de consultas | ✅ |
| Clima simulado con marcas horarias, `uvMaxDia` y temperatura por hora | ✅ |
| Modo "sin pronóstico" (`PUT /api/demo/weather/offline`) | ✅ |
| Escenarios por regla en el simulador (`src/scenarios/`) | ✅ (6 reglas actuales migradas) |
| Escenarios de las reglas nuevas | 🚧 (S-01 `LOCK`, S-03 `INVALID_READING` y S-04 `INCOMPLETE_HARDWARE` hechos; cada cambio agrega el suyo) |
| Retiro de reglas v1 (`ShadingRule`, `SupplyRule`, `DailyDoseLimitRule`, `FollowUpRule`) | ⬜ (ver tabla del índice) |
