# Design: add-plan-nutricion

## Estado verificado
- No existe regla ni servicio de nutrición. La CE llega como métrica de la zona (`ce`, µS/cm → persistida en dS/m).
- Verificado: no hay historial de lecturas. N-02 usa `ReadingHistory.average(zone, ReadingMetric.CE, window)` (24 h, `onlyPulse()`) de
  `add-historial-lecturas`, que es prerrequisito.

## Decisiones tomadas (22/10/2026)

Lo que `tasks.md` 0.1 dejaba abierto, más los desvíos que salieron de implementar.

| Id | Decisión | Por qué |
|---|---|---|
| D-1 | **`ZoneRuleEngine` se crea en este cambio** (`engine/zone/`), no se reutiliza | `add-mediasombra-v2` se canceló: nadie lo había creado. `ZoneRule` es `ConsumidorParametros` + `DemoControllable` (no extiende `Rule`: su `evaluate` recibe un `ZoneRuleContext`, no el contexto de un sector). Corre **una vez por zona**, en `NurseryService.updateTelemetry` después del bucle de sectores y sólo con el motor encendido. Cada regla ejecuta sus acciones antes de la siguiente, así N-02 adelanta y N-01 ya lo ve vencido en el mismo pulso |
| D-1b | **No hay un tick propio «en la apertura de cada ventana»** | Evaluar al cierre del pulso alcanza: el plan sólo *pide* la aplicación; cuándo se aplica cada sector lo resuelve el `ApplicationService` y su `PendingApplicationScheduler` (cada 5 min, con las ventanas como parámetros del catálogo). Atar un tick a 07:00 y 17:00 las desfasaría al editarlas |
| D-2 | **La tanda se cierra por pendientes, y su hora es la de la última aplicación** | Se cuenta `pending_application` de la zona/insumo/regla; con cero, `last_plan_application_ts` = el `ts` de la última aplicación de la tanda (o la hora de la decisión si ninguna se aplicó). Así una tanda que tardó un día en completarse no acorta el siguiente intervalo. Idempotencia: con la tanda abierta N-01 no pide nada (`nutrition_batch_start_ts`) y el pedido por sector ya era idempotente (a lo sumo una pendiente por sector e insumo) |
| D-3b | **N-02 usa `ReadingHistory.latest(zona, n, onlyPulse)`** y no una media por ventana de 24 h | «6 lecturas» es la unidad de la v2 y de `nutricion.ce-lecturas`; con otro pulso (60–360 min) 24 h no serían 6 lecturas. Las lecturas sin CE no cuentan. El bypass `samples` baja a la última lectura de cualquier origen |
| D-4 | **Una alerta por evento = estado, no un registro de «ya avisé»** | N-02 no actúa mientras el plan está adelantado o en curso; cuando N-01 aplica, la fertilización reciente corta la condición sola. N-01 alerta sólo al iniciar la tanda |
| D-5 | **Confirmado**: una zona sin aplicaciones del plan está vencida | Efecto a conocer: con N-01 encendida el primer pulso con motor de cada zona pide la tanda. Un tanque sin registrar rechaza cada aplicación y la tanda se cierra descartada; hay que cargar los tanques antes |
| D-6 | `nutricion.intervalo-plan` es el valor inicial; la zona puede fijar el suyo (`NULL` = el del catálogo) | Sin migración ni siembra por zona: una zona nueva o recién regenerada usa el general. El rango de la zona es el del parámetro (3–30) |
| D-7 | **Bloqueos y hardware** | Zona bloqueada (S-01): el plan queda vencido y espera, no se descarta la tanda. Sector bloqueado, sin bomba (S-04) o con falla hidráulica (S-05): fuera de esa tanda, con su motivo en el Registro de Inacción. Es lo mismo que cortan las reglas por sector en la rama de insumos |
| D-8 | **Dosis** | La del catálogo (`insumo.dosis-fertilizante`, 10 ml de fábrica, 1–50): la v2 dice 100 mL pero el firmware sólo acepta 50 ml por comando (ver `add-dosificacion-base`). El plan no tiene una dosis propia |
| D-9 | **`SupplyRule` y `DailyDoseLimitRule` siguen retiradas, no se borran** | N-01 no decide con el diagnóstico de IA, así que no las reemplaza: lo harán las reglas por diagnóstico F-*. `add-dosificacion-base` D-9 decía «cuando entre la primera regla N/F»; se lee como la primera por diagnóstico |
| D-10 | **Nombres en inglés** | `NutritionPlanRule`/`LowEcRule`, `ZoneRule`/`ZoneRuleEngine`, `NutritionPlanService`, columnas `nutrition_*`/`last_plan_application_ts`, claves de override `interval`, `no-fertilized`, `samples`. Los textos para el usuario, las claves del catálogo (`nutricion.*`) y las rutas de la API (`/api/zonas/{zona}/plan-nutricion`) siguen la convención del repo |
| D-11 | **El Inspector** muestra las reglas de zona en la rama `INSUMO` | El esquema (`/api/rules/schema`) y el catálogo (`/api/rules/parametros`) las incluyen, y `GET /api/rules/evaluaciones/{sectorId}` suma la última traza de la zona a la del sector (`ZoneTraceStore`). No hay un grafo aparte por zona |

## Decisiones

### D-1 — Evaluación por MZ, aplicación por sector
Como la mediasombra, N-01 y N-02 son **de zona** (el plan es por MZ; la CE es de la MZ). Se evalúan en el cierre del
pulso y en la apertura de cada ventana de aplicación. Generan una `aplicacion_pendiente` por sector (100 sectores) ⇒
el trabajo real lo hace `add-dosificacion-base`. Usa un `ZoneRuleEngine` / `ZoneRule` (verificado: el motor sólo evaluaba por sector). `add-mediasombra-v2` se canceló, así que **este cambio lo crea** (ver «Decisiones tomadas»).

### D-2 — "Última aplicación del plan" de la MZ
Campo `ultima_aplicacion_plan` en la zona, escrito cuando se **completa** la tanda (todos los sectores aplicados o
descartados con motivo), no al primer sector. Si el plan arranca y algunos sectores quedan diferidos, N-01 no se
vuelve a disparar para ellos: siguen pendientes (idempotencia por `ciclo_id`).

### D-3 — N-02
Media de las últimas **6 lecturas** (24 h con pulso de 4 h) de CE de la MZ. Si hay menos de 6 lecturas ⇒ `SIN_DATO`,
no actúa. "No hubo fertilización en 5 días" mira **cualquier** aplicación de fertilizante en la MZ (plan o correctiva),
con el intervalo de 5 días como parámetro. Adelantar = fijar `ultima_aplicacion_plan` de modo que N-01 venza ya
(`proximo_plan = ahora`), sin tocar el historial de aplicaciones.

### D-4 — Una alerta por evento
N-02 alerta al activarse y no vuelve a hacerlo mientras el plan adelantado no se haya ejecutado (principio 8).

### D-5 — Primera ejecución
Una MZ sin aplicaciones previas: se considera "vencida" ⇒ N-01 aplica en la próxima ventana. (La v2 no lo dice;
propuesta, confirmar.)

### D-6 — Parámetros
| Clave | Fábrica | Rango |
|---|---|---|
| `nutricion.intervalo-plan` (por MZ) | 7 d | 3–30 |
| `nutricion.ce-baja` | 0,5 dS/m | 0,3–1,0 |
| `nutricion.ce-lecturas` | 6 | 3–12 |
| `nutricion.sin-fertilizar-dias` | 5 d | 3–14 |
`intervalo-plan` es por zona (columna en la zona), no del catálogo global; el global es el valor inicial.
