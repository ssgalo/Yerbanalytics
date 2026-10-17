# Design: add-mediasombra-v2

`be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`; `fe/` = `Desarrollo/frontend/src/`.

## Estado verificado

| Pieza | Hoy |
|---|---|
| Regla | `be/engine/rules/ShadingRule.java` (prio 12, rama MEDIASOMBRA): UV ≥ `mediasombra.uv-umbral` ⇒ apertura protectora; si no, plan por día de ciclo |
| Parámetros | `ParametrosMediasombra`: `uv-umbral` (7), `apertura-proteccion-uv` (30 %), `apertura-maxima` (100 %) |
| Plan | `RustificacionEtapaEntity` (`diaDesde`, `diaHasta`, `aperturaPct`), global; fecha de siembra en `application.properties` |
| Acción | `MOVER_MEDIASOMBRA` con `[apertura=N]` en el texto, parseado por `ActionExecutor` |
| Contrato | `shade SET {targetPct}`: `>= MEDIASOMBRA_UMBRAL_APERTURA_PCT` enrolla, si no desenrolla (`contrato.h`) ⇒ ya sirve para 2 estados |
| Pronóstico | `WeatherForecast`: `uvIndex`, `tempC`, `humRel` **de la hora actual** + `forecastSlots(label, uv, rain)`; los slots **no traen temperatura** |
| Frontend | `PlanRustificacion{zonaId, fechaInicio, etapas[{orden, dias, abiertaDesde, abiertaHasta}], estado}` ya existe; backend no |
| Evaluación | Verificado: `RuleOrchestrator.evaluate(ctx)` recibe un `RuleContext` **de un sector**; `NurseryService.updateTelemetry` lo llama en un bucle por los sectores de la zona (dentro de `if (ejecutaMotor)`). **No existe un camino de evaluación por zona** |
| Hardware | La mediasombra se registra **por sector** (una por sector, `HardwareService`); el comando MQTT también es por sector (`.../sector/{sector}/command`). La v2 la comanda por MZ |
| Comando | `ActionExecutor` publica `shade SET {targetPct}` por sector y escribe `sector.actuadorShade` |
| Frontend (http) | `httpRepository` llama a `GET /api/zonas/{id}/rustificacion`, `POST .../rustificacion` (iniciar) y `POST .../rustificacion/cancelar`; el backend **no implementa ninguna**. No hay "resetear" en el front |

## Decisiones

### D-1 — Evaluación por macro-zona (verificado: hoy no existe)
`RuleOrchestrator` sólo evalúa un `RuleContext` de sector y el bucle de `updateTelemetry` recorre los sectores de la zona.
M-01/M-02 son por MZ y se evalúan por **franja** y con cada lectura; dentro de ese bucle las repetiríamos 100 veces. Decisión:
`MediasombraZonaService` con su propio mini-contexto (`ZonaContext`: zona, lectura, pronóstico, plan, bloqueo, hora) que corre las
reglas de zona (`M-01`, `M-02` y las que sumen otros cambios: F-S2, N-01, N-02) y emite **una decisión por MZ**. Se invoca (a) desde
`updateTelemetry`, **después** del bucle de sectores y sólo si `ejecutaMotor`, con la misma lectura y el mismo pronóstico; y (b) desde el
tick de `SensingScheduler` en cada cambio de franja. **Este camino por zona es compartido** por `add-plan-nutricion` y
`add-reglas-clorosis-estres-solar` (F-S2): se crea acá, una vez, como `ZoneRuleEngine`, y sus reglas implementan `ZoneRule`
(que también es `DemoControllable`, ver `add-control-demo-reglas`).
**Cómo se traduce a comandos:** como el hardware y el comando son por sector, la decisión de la MZ se aplica a **cada sector de la MZ que tenga una
mediasombra registrada** (y su `sector.actuadorShade`), publicando un comando por cada una.

### D-2 — Un estado objetivo por MZ y "gana cerrar"
`MediasombraPedido(estado ∈ {CERRADA, ABIERTA}, motivo, hasta?)`. Se reúnen los pedidos de todas las reglas y se
resuelve: si alguno pide CERRADA ⇒ CERRADA. Esto es el principio 4 y deja a F-S2 (otro cambio) como una regla más.
`hasta` permite que M-02 mantenga "cerrada hasta las 16:00" sin re-evaluar la condición.

### D-3 — Estado de la protección
M-02 activa necesita memoria ("no se reabre antes de las 16:00 aunque la condición desaparezca"). Se guarda en la
zona `proteccion_hasta` (epoch ms). Mientras `ahora < proteccion_hasta` ⇒ CERRADA sin reevaluar. Se limpia al vencer.

### D-4 — Pronóstico horario
Verificado: `WeatherForecast` tiene `horas` (`PronosticoHora`: hora, probabilidad, mm) pero **no temperatura por hora ni UV máximo del día**
(`tempC` y `uvIndex` son los de la hora actual). Se extiende la consulta a Open-Meteo (`temperature_2m` hourly y
`uv_index_max` daily; ya se consulta con `timezone`) y `WeatherForecast.temperaturaHora(LocalDateTime)`.
**La extensión del modelo y del clima simulado se implementa en `add-control-demo-reglas` §2** (es la misma pieza); acá sólo se consume.
*Esto es lo que M-02 llama "temperatura = máx(lectura, pronóstico de esa hora)".* Sin pronóstico ⇒ sólo la lectura (O-02).

### D-5 — Plan: etapas con más de una ventana (DIVERGENCIA con el front)
La v2 §7 tiene etapas con **dos** ventanas abiertas (etapa 2: 07–11 y 16–19; etapa 3: 07–12 y 15–19). El front
modela una sola (`abiertaDesde/abiertaHasta`). Opciones:
- (a) Cambiar a `ventanas: [{desde, hasta}]` en el tipo, el formulario y el backend. **Recomendada.**
- (b) Partir esas etapas en dos etapas de `dias` ficticios: no representa el plan.
Implica tocar `fe/types/domain.ts`, la tarjeta de Rustificación y sus tests (cambio acotado).

### D-6 — Ciclo de vida
Estados: `programado | en_curso | finalizado | cancelado` (el front ya los tiene).
- **Iniciar:** el día de inicio es el día 1; rige desde la próxima franja.
- **Cancelar:** la MZ vuelve **en el momento** al perfil de crecimiento; `INFO`.
- **Fin (automático):** pasado el último día, `finalizado`, y la MZ **sigue con el horario de la última etapa**
  hasta resetear; `INFO` "Plan finalizado: plantines listos para trasplante".
- **Resetear:** vuelve al perfil de crecimiento.
Un plan por MZ a la vez (índice único parcial sobre estados vivos).

### D-7 — Horario por defecto
Perfil de crecimiento (08–10 abierta) en `ConfiguracionOperativa`; si hay plan, el plan manda.

### D-8 — Respetar bloqueo y falla
Si la MZ está bloqueada (S-01) o con falla mecánica (S-05), el servicio no emite el comando; sí registra la
decisión en la traza. Los tests de este cambio fijan el contrato de ese punto de enganche.

### D-9 — Retiro
Se borra `ShadingRule`, sus parámetros UV/apertura (migración manual de `umbral`/parámetros vigentes) y
`rustificacion_etapa` (`resources/migracion-quitar-rustificacion-global.sql`, mismo patrón que las demás). Los
parámetros UV pasan a `mediasombra.uv-extremo` (11) y se suman los de §11 (ver spec).

## Parámetros nuevos (familia MEDIASOMBRA)
| Clave | Fábrica | Rango |
|---|---|---|
| `mediasombra.ventana-proteccion` | 11:00–16:00 | — |
| `mediasombra.temp-calor-extremo` | 35 °C | 32–40 |
| `mediasombra.temp-con-sol` | 32 °C | 28–35 |
| `mediasombra.temp-critica` | 38 °C | 35–42 |
| `mediasombra.luz-sol-directo` | 85 % | 60–100 |
| `mediasombra.uv-extremo` | 11 | 8–13 |
Restricción cruzada: `temp-con-sol` < `temp-calor-extremo` < `temp-critica`.
(`perfil de crecimiento` y duración del plan, 20–60 días, van en configuración/plan, no en el catálogo.)

## Preguntas abiertas
- ¿Quién inicia un plan si el rol de Ingeniero Agrónomo no está presente? (v2: sólo él.)
- Migrar planes existentes: no hay nada que migrar (verificado: el backend no implementa los endpoints que el modo `http` del front ya llama;
  los planes del front sólo existen en el mock). Los endpoints deben respetar las rutas que el front ya usa: `GET/POST /api/zonas/{id}/rustificacion`
  y `POST .../cancelar`, y se **agrega** `POST .../resetear` (la v2 lo pide y el front no lo tiene).
