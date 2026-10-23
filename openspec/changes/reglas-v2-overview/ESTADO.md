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
| 3 | `add-fallas-esp32` | ✅ | `feat/reglas-v2-fallas-esp32` | C, 2 | S-05. Tópico `nursery/zone/{zona}/errores`, `HardwareFaultService` (único que escribe `dispositivo.falla`), `HydraulicFaultRule` (prioridad 2) y `ShadeFaultRule`, `POST /api/hardware/{id}/reparacion` con permiso `hardware.reparar` (el Operario ahora ve Hardware). Migración manual de permisos (`migracion-permisos-hardware-reparar.sql`). Un sector con falla hidráulica deja de regarse solo hasta registrar la reparación. Firmware **sin compilar** (sin toolchain); el caudalímetro queda detrás de `ENVIAR_ERRORES_CAUDAL` |
| 4 | `add-metrica-dpv` | ✅ | `feat/reglas-v2-metrica-dpv` | C, H | §1.4. `VaporPressureDeficit` (puro) y `VaporPressureBand`; la ingesta lo calcula con T y HR de la lectura y lo guarda en `zona.dpv`/`dpv_ts` y `zone_reading.dpv` (sin migración manual). `GET /api/nursery` → `lectura.dpv`; fila informativa en el detalle de zona. Habilita 8 (F-F1, F-F2, F-P1) |
| 5 | `add-mediasombra-v2` | ❌ cancelado | — | C | Cancelado: se borró su documentación OpenSpec. M-01, M-02, §7 y el `ZoneRuleEngine` quedan sin dueño; `ShadingRule` sigue moviendo la malla |
| 6 | `add-dosificacion-base` | ✅ | `feat/reglas-v2-dosificacion-base` | C, 2 | §8.1–8.3 y P-D1…P-D5. `ApplicationService` (único camino para aplicar), `ApplicationPreconditions` (`DemoControllable`), `TankService` y API `/api/tanques`, reingreso del sector, aplicaciones diferidas. Decisiones: **una bomba por sector** (D-2 b: sin tocar contrato ni firmware), tanque por insumo y por MZ, **`SupplyRule`/`DailyDoseLimitRule` retiradas pero no borradas** (arrancan apagadas: aún no hay N-*/F-*). Dosis 10 ml de fábrica, máx 50 (tope del firmware). Migraciones manuales: `migracion-permisos-insumos.sql`, `migracion-dosis-por-insumo.sql`. Pendiente en 7/8/9: que sus reglas pidan la aplicación; el borrado de la v1 |
| 7 | `add-plan-nutricion` | ✅ | `feat/reglas-v2-plan-nutricion` | C, H, 6 | N-01, N-02. Crea el **`ZoneRuleEngine`** (`engine/zone/`: `ZoneRule`, `ZoneActionExecutor`, `ZoneTraceStore`; las reglas de macro-zona corren una vez por zona al cierre del pulso con motor) y `nutrition/` (`NutritionPlanRule`, `LowEcRule`, `NutritionPlanService`). Una *tanda* pide fertilizante en cada sector a `ApplicationService` y el intervalo corre desde que se aplicó el último; intervalo por zona (3–30 d) en el detalle de la macro-zona (`PUT /api/zonas/{zona}/plan-nutricion`). Sin migración manual (columnas nuevas de `zona`). Escenarios `NUTRITION_PLAN` y `LOW_EC` en el simulador. Dosis la del catálogo (10 ml), no 100 mL. Efecto a conocer: con N-01 encendida el primer pulso de cada zona pide la tanda, y con el tanque sin registrar se descarta |
| 8 | `add-reglas-biotico` | ✅ | `feat/reglas-v2-reglas-biotico` | C, H, 4, 6 | F-F1, F-F2, F-P1, F-04, F-06. Reglas de sector `FungalDamageRule` y `FoliarPestRule` (esqueleto `BioticDiagnosisBase`) y de macro-zona `FungalConditionRule` y `OutbreakRule` (`biotic/`); el tope es una precondición más de `ApplicationPreconditions` con la revisión en `POST /api/sectores/{id}/revision-fitosanitario` (permiso `insumos.gestionar`, sin migración manual). El diagnóstico se da por usado al ejecutar la aplicación (D-4). Decisiones por defecto a validar con el vivero: el *Daño biótico* genérico no dispara nada (D-1) y los lindantes salen de la grilla visual (D-6). Dosis la del catálogo (10 ml). `SupplyRule`/`DailyDoseLimitRule` siguen retiradas: faltan F-C*/F-S* |
| 9 | `add-reglas-clorosis-estres-solar` | 🚧 | `feat/reglas-v2-clorosis-estres-solar` | C, H, 5, 6 | **F-C1, F-C2, F-S1 y F-S3 ✅; F-S2 ⬜ pendiente, desbloqueada** (cerrar la malla de la zona de 11 a 16 h por 3 días necesitaba un dueño de la malla por horario; desde #66 lo es `HorarioMediasombraService`). `ChlorosisNonNutritionalRule` corta la rama de insumos (retira también el pedido de F-C2 que esperaba), `ChlorosisNutritionalRule` fertiliza, `SolarStressRule` repone agua por el despacho y `SolarStressUnexplainedRule` avisa. Compuerta común `engine/DiagnosisGate`, rustificación con el plan **de la macro-zona** detrás de `HardeningPhase`. Sin migración manual. De paso: `RuleContext.metricRaw` ya no lanza ante una métrica sin lectura (sonda de CE/pH sin conectar) |
| 10 | `add-efectividad-y-s06` | 🚧 | `feat/reglas-v2-efectividad-s06` | C, 6, 8, 9 | **E-01…E-03, S-06, F-05 y la revisión humana ✅**; falta el escenario «Riego efectivo» del simulador (la lectura fijada no puede subir después del riego). `EffectivenessJob` reemplaza a `FollowUpRule` (borrada); `IneffectiveActionRule` (prio 7) bloquea el riego de la zona y una precondición de `ApplicationPreconditions` bloquea los tratamientos del sector. El nodo testigo admite un sector opcional. `DailyDoseLimitRule` (retirada) pasó a la prioridad 28. Migración: `migracion-permisos-efectividad.sql` |
| 11 | `add-operacion-sin-internet` | ✅ | `feat/reglas-v2-sin-internet` | C, 5, 6 | **O-01, O-03, O-04 ✅; O-02 parcial** (M-02 no existe: `add-mediasombra-v2` se canceló; la `ShadingRule` de zona sin pronóstico no cierra por UV y la malla sigue el horario); **O-05 no aplicable** (no hay destino remoto de eventos, ver `reglas_v2.md` §10). `WeatherService.status()` (`ForecastStatus`), `Rule.supuestoSinPronostico()` y `TrazaRegla.supuesto`, `ForecastModeMonitor` (un `INFO` al entrar y otro al salir) y el modificador `NO_FORECAST` del simulador. Sin migración manual |

## Reglas de reglas_v2 por estado

| Regla / sección | Estado | Dueño |
|---|---|---|
| R-01, R-03, R-04, R-05, R-06 | ✅ ya estaban (parámetros verificados contra §11) | — |
| R-02 (tope en 0) | ⚠️ tope de 12 h siempre aplicado (desviación documentada); con S-06 en pie (10) se **decidió mantenerlo** | 0 → 10 |
| S-01 | ✅ (mediasombra por zona: lo cierra 5, tarea 0.4) | 1 |
| S-02 | ✅ (escalamiento por pulso, no por intento: D-6) | 0 |
| S-03, S-04 | ✅ | 2 |
| S-05 | ✅ | 3 |
| S-06 | ✅ | 10 |
| §1.4 DPV | ✅ | 4 |
| M-01, M-02, §7 | ❌ cancelado | 5 |
| §8.1–8.3 | ✅ (la pide N-01; las reglas por diagnóstico F-* todavía no) | 6 |
| N-01, N-02 | ✅ | 7 |
| F-F1, F-F2, F-P1, F-04, F-06 | ✅ | 8 |
| F-C1, F-C2, F-S1, F-S3 | ✅ | 9 |
| F-S2 | ⬜ pendiente, desbloqueada por #66 (`HorarioMediasombraService`, ver la fila 9) | 9 |
| E-01…E-03, F-05 | ✅ | 10 |
| O-01, O-03, O-04 | ✅ | 11 |
| O-02 | ⚠️ parcial (sin M-02) | 11 |
| O-05 | ➖ no aplicable (sin destino remoto) | 11 |

## Infraestructura transversal

| Pieza | Estado |
|---|---|
| Toda pieza de decisión es `DemoControllable` y aparece en `GET /api/demo/rules` | ✅ |
| Historial de lecturas por zona (`zone_reading`) y su servicio de consultas | ✅ |
| Clima simulado con marcas horarias, `uvMaxDia` y temperatura por hora | ✅ |
| Modo "sin pronóstico" (`PUT /api/demo/weather/offline`) | ✅ |
| Escenarios por regla en el simulador (`src/scenarios/`) | ✅ (6 reglas actuales migradas) |
| Escenarios de las reglas nuevas | 🚧 (F-F1 `FUNGAL_DAMAGE`, F-P1 `FOLIAR_PEST`, F-F2 `FUNGAL_CONDITION`, F-04 `OUTBREAK`, F-06 `FUNGICIDE_CAP`, F-C2 `CHLOROSIS_NUTRITIONAL`, F-C1 `CHLOROSIS_NON_NUTRITIONAL`, F-S1 `SOLAR_STRESS`, F-S3 `SOLAR_STRESS_UNEXPLAINED`, S-01 `LOCK`, S-03 `INVALID_READING`, S-04 `INCOMPLETE_HARDWARE`, S-05 `FAULT_NO_FLOW`/`FAULT_OVERFLOW`/`FAULT_SHADE_LIMIT`/`REPAIR`, N-01 `NUTRITION_PLAN`, N-02 `LOW_EC` y el modificador `APPLICATION_PRECONDITIONS` hechos; cada cambio agrega el suyo) |
| Retiro de reglas v1 (`ShadingRule`, `SupplyRule`, `DailyDoseLimitRule`, `FollowUpRule`) | 🚧 `SupplyRule` y `DailyDoseLimitRule` **retiradas** (arrancan apagadas, `yerbanalytics.reglas.retiradas`), sin borrar hasta que entren las reglas por diagnóstico F-C*/F-S* (N-01 no decide con el diagnóstico; las bióticas F-F1/F-P1 sólo cubren hongo y plagas); la `ShadingRule` por sector la reemplazó la de zona (#66) y `FollowUpRule` la borra `add-efectividad-y-s06` |
| Escenarios previos del simulador (R-01…R-04, `SHADING`, `SUPPLY`, `LOCK`) con lecturas determinísticas | ⚠️ **Pendiente.** Publican la telemetría *antes* del pulso, pero la respuesta al pulso (la que decide el motor) la arma la emisión automática al azar y la pisa: pueden no disparar. Se arregla fijando sus lecturas con `pinnedReadings`, como ya hacen `INVALID_READING` e `INCOMPLETE_HARDWARE` (no se tocó en `add-seguridad-lectura-hardware`) |
