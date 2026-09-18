# Release 2 — Automatización Agronómica Configurable y Alertas Inteligentes

## Objetivo

Convertir los diagnósticos del sistema en acciones agronómicas automáticas,
calibrables por el agrónomo y supervisables de forma segura por el operario,
manteniendo informado al productor mediante alertas e incorporando el pronóstico
climático como insumo de decisión.

**HUs incluidas:** HU-05, HU-07, HU-08, HU-09, HU-10, HU-15, HU-19

**Fecha estimada:** 19/09

---

## Specs de la Release 2

### Nuevas en esta release

| Spec | HU | Estado | Descripción |
|---|---|---|---|
| [`motor-reglas`](../specs/motor-reglas/spec.md) | HU-06/07/08/09/10/19 | ✅ Implementado | Pipeline de reglas: orquestador, ActionExecutor, triggers reactivo+Watchdog, tópicos MQTT |
| [`bloqueo-manual`](../specs/bloqueo-manual/spec.md) | HU-19 | ⚠️ Parcial | Motor OK (`BloqueoManualRule`, entidad, repository). **Falta:** `BloqueoController` (REST API) |
| [`pronostico-climatico`](../specs/pronostico-climatico/spec.md) | HU-09 | ✅ Implementado | `WeatherService` (cache thread-safe, retry), `ClimaOverrideRule`, `WeatherClient` intercambiable |
| [`alertas-inteligentes`](../specs/alertas-inteligentes/spec.md) | HU-10 | ⚠️ Parcial | Frontend OK (`AlertsDropdown`, tipo `Alert`, mock). **Falta:** `AlertaService`, `AlertaController`, generación desde `ActionExecutor` |
| [`dosificacion-insumo`](../specs/dosificacion-insumo/spec.md) | HU-07 | ✅ Implementado | `InsumoRule` (gate IA ≥ 85%), `DosisLimiteRule` (bloqueo 24h), comando MQTT |
| [`rustificacion-mediasombra`](../specs/rustificacion-mediasombra/spec.md) | HU-08 | ✅ Implementado | `MediasombraRule` (plan etapas + UV), `ActionExecutor` MOVER_MEDIASOMBRA, Watchdog proactivo |
| [`diagnostico-consulta`](../specs/diagnostico-consulta/spec.md) | HU-05 | ✅ Implementado | `DiagnosticsPage` + `DiagCard` + `DiagFilters` + `PhotoModal` |

### Completadas en releases anteriores (base para R2)

| Spec | HU | Descripción |
|---|---|---|
| [`configuracion-agronomica`](../specs/configuracion-agronomica/spec.md) | HU-15 | Vista frontend de configuración: umbrales, límites, plan de rustificación y parámetros de seguimiento |
| [`configuracion-persistencia`](../specs/configuracion-persistencia/spec.md) | HU-15 | Backend: persistencia, validación fisiológica y auditoría de la configuración agronómica |
| [`historial-persistencia`](../specs/historial-persistencia/spec.md) | HU-11 (base R2) | Backend: historial inmutable con seguimiento de efectividad post-acción |
| [`historial-trazabilidad`](../specs/historial-trazabilidad/spec.md) | HU-11 (base R2) | Frontend: timeline global de acciones con cadena de justificación y filtros |
| [`diagnosticos-registro`](../specs/diagnosticos-registro/spec.md) | HU-05 (base) | Backend: alta y consulta de diagnósticos anclados a capturas |

---

## Pendientes de la Release 2

### ⚠️ `BloqueoController` — HU-19 (REST API de bloqueo manual)

El motor respeta los bloqueos (`BloqueoManualRule` leyendo `BloqueoManualRepository`),
pero no existe forma de crear/listar/desactivar bloqueos desde el exterior sin
acceder directamente a la BD.

**Lo que falta implementar:**
- `BloqueoController` → `GET/POST/DELETE /api/bloqueos`
- `BloqueoService` (lógica de activación, desactivación, registro en historial)
- DTO de request/response
- Integración frontend (vista o panel de operario — probablemente en `sector-detail`)

### ⚠️ `AlertaService` / `AlertaController` — HU-10 (alertas persistidas)

El frontend muestra el panel de alertas con el mock (`generators.ts`), pero en modo
backend las alertas se obtendrían del endpoint `/api/alertas` que no existe.
Actualmente, las "alertas" son el conteo de sectores en warning/critical del snapshot.

**Lo que falta implementar:**
- Entidad `AlertaEntity` + `AlertaRepository`
- `ActionExecutor` → emitir alertas estructuradas al ejecutar/abortar acciones
- `AlertaController` → `GET /api/alertas` (filtros: `sector`, `zona`, `severidad`, `leida`)
- `PATCH /api/alertas/{id}/leida` → marcar como leída
- `HttpRepository.getAlertas()` → conectar el frontend con el backend

---

## Arquitectura del Motor de Reglas (R2)

```
MQTT telemetry
     ↓
NurseryService.updateTelemetry()
     ↓
RuleOrchestrator.evaluate(RuleContext)
     │
     ├─ [0]  BloqueoManualRule  → ABORT_ALL (corta cadena)
     ├─ [1]  StaleSensorRule    → ABORT_RIEGO (sensor stale)
     ├─ [2]  ClimaOverrideRule  → POSTPONE_RIEGO (lluvia inminente)
     ├─ [5]  DosisLimiteRule    → ABORT_INSUMO (límite 24h)
     ├─ [10] RiegoRule          → ACTIVAR_VALVULA
     ├─ [11] InsumoRule         → ACTIVAR_BOMBA (confianza IA ≥ 85%)
     └─ [12] MediasombraRule    → MOVER_MEDIASOMBRA (plan/UV)
          ↓
     ActionExecutor
     │
     ├─ MQTT publish → nursery/zone/{z}/sector/{s}/command
     ├─ Persistir historial_evento
     └─ Emitir alertas por severidad
```

## Tópicos MQTT de la Release 2

| Tópico | Dirección | Uso |
|---|---|---|
| `nursery/zone/{z}/sector/{s}/telemetry` | ESP32 → Backend | Ingesta de sensores |
| `nursery/zone/{z}/sector/{s}/command` | Backend → ESP32 | Comandos a actuadores (QoS 1 — nodo idempotente) |
| `nursery/zone/{z}/sector/{s}/ack` | ESP32 → Backend | Confirmación de ejecución |

## Variables de entorno clave

| Variable | Default | Descripción |
|---|---|---|
| `YERBANALYTICS_ENGINE_LLUVIA_UMBRAL_PCT` | 60.0 | Umbral de probabilidad de lluvia para posponer riego |
| `YERBANALYTICS_ENGINE_UV_UMBRAL` | 7.0 | Umbral de índice UV para protección de mediasombra |
| `YERBANALYTICS_WEATHER_CACHE_TTL_MS` | 900000 | TTL de la cache del pronóstico climático |
| `YERBANALYTICS_WEATHER_MAX_RETRIES` | 3 | Reintentos ante fallos de la API climática |
| `ACTION_COOLDOWN_MINUTES` | 30 | Minutos mínimos entre comandos repetidos al mismo sector |
| `YERBANALYTICS_NURSERY_FECHA_SIEMBRA_ISO` | — | Fecha de inicio del ciclo (formato `yyyy-MM-dd`) |
