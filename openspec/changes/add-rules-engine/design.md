# Design: add-rules-engine

## Context

El backend Spring Boot actual procesa la telemetría MQTT en
`NurseryService.updateTelemetry()`, donde las decisiones de riego e insumo se
resuelven inline contra los umbrales. El seguimiento post-acción ya vive
separado en `HistorialService.evaluarSeguimiento()` (scheduler). La
configuración agronómica (umbrales, límites, rustificación) ya está persistida
y validada en `ConfiguracionService`.

El motor de reglas **no reemplaza** esos servicios: los consume como
proveedores de datos y les delega la persistencia. Lo que cambia es **dónde
vive la lógica de decisión**: sale de `NurseryService` y pasa a componentes
autocontenidos, testeables y encadenables.

## Goals / Non-Goals

**Goals**
- Extraer la lógica de decisión en reglas autocontenidas, puras y unitariamente
  testeables.
- Soportar reglas bloqueantes (bloqueo manual, sensor stale) que cancelan la
  actuación antes de que ocurra.
- Integrar el pronóstico climático (HU-09) como input del árbol de decisiones.
- Integrar el confidence score de la IA (HU-04) como gate de dosificación.
- Dejar preparada la extensibilidad: agregar una regla = agregar un `@Component`
  que implemente `Rule`.

**Non-Goals**
- No se implementa el modo offline (HU-13) en esta iteración; pertenece a R4
  y requiere un motor embebido en el microcontrolador.
- No se implementa la UI de bloqueo manual; solo la tabla y la regla. El
  endpoint REST y la pantalla son cambios aparte.
- No se modifica la interfaz del frontend ni los DTOs existentes del snapshot
  (`NurseryData`). Las acciones del motor se reflejan en los mismos campos
  (`actuador_valve`, `actuador_pump`, `actuador_shade`, `historial_evento`).

## Decisions

### 1. Patrón: Rule como Strategy + Chain of Responsibility

Cada regla implementa una interfaz `Rule` con un método `evaluate(RuleContext)`
que devuelve una lista de `RuleAction`. El `RuleOrchestrator` las itera en
orden de prioridad y corta la cadena si una acción es bloqueante.

```
Rule (interface)
  ├── BloqueoManualRule    prio 0   (R3 - HU-19)
  ├── StaleSensorRule      prio 1   (R1 - HU-02 CA-03/04)
  ├── DosisLimiteRule      prio 5   (R3 - HU-07 CA-03)
  ├── ClimaOverrideRule    prio 2   (R2 - HU-09)
  ├── RiegoRule            prio 10  (R3 - HU-06)
  ├── InsumoRule           prio 11  (R3 - HU-07)
  ├── MediasombraRule      prio 12  (R3 - HU-08)
  └── SeguimientoRule      prio 20  (R4 - HU-12)
```

> **¿Por qué no un motor genérico (Drools, Easy Rules)?**
> El dominio tiene ~8 reglas con lógica clara y acotada. Un motor genérico
> agrega una dependencia pesada y un DSL que el equipo no maneja, sin aportar
> valor real. Reglas en Java puro = cero curva de aprendizaje y debug trivial.

### 2. RuleContext: Value Object inmutable

Un snapshot de **todo lo que una regla necesita** para decidir, construido una
vez por ciclo de evaluación y compartido por todas las reglas. Ninguna regla
modifica el contexto; solo emite acciones.

```java
public record RuleContext(
    SectorEntity sector,
    ZonaEntity zona,
    List<MetricSpec> specs,
    List<Metric> metrics,
    ConfiguracionOperativaEntity config,
    WeatherForecast forecast,        // null si la API falló
    DiagnosisResult diagnosis,       // null si IA no corrió
    boolean bloqueoManualActivo,
    boolean sensorStale,
    Instant now
) {}
```

### 3. ActionExecutor: materialización de las decisiones

Único punto donde las acciones se convierten en efectos reales:

- **MQTT publish** al broker para comandar actuadores (protocolo simétrico a
  la ingesta de telemetría).
- **Persistencia** en `historial_evento` con la cadena completa
  `Lectura → Decisión → Acción` (HU-11 CA-01).
- **Emisión de alertas** clasificadas por severidad (HU-10).
- **Actualización** de campos del `SectorEntity` (`actuador_valve`,
  `actuador_pump`, `actuador_shade`).

### 4. WeatherService: cliente de API climática (HU-09)

- Consulta asíncrona con timeout de 10 s (HU-09 CA-01).
- Hasta 3 reintentos con exponential backoff si el servidor no responde en
  15 s (HU-09 CA-02).
- Si los reintentos fallan: `forecast = null`, se loguea un warning de
  degradación de servicio, y el motor opera solo con sensores (HU-09 CA-03).
- Cache local del último forecast válido para minimizar llamadas.

### 5. Tabla `bloqueo_manual` (HU-19)

```sql
CREATE TABLE IF NOT EXISTS bloqueo_manual (
    id BIGSERIAL PRIMARY KEY,
    sector_id VARCHAR(255),          -- null = bloquea toda la zona
    zona_id VARCHAR(255) NOT NULL,
    activado_por VARCHAR(255) NOT NULL,
    activado_ts BIGINT NOT NULL,
    motivo VARCHAR(255),
    activo BOOLEAN NOT NULL DEFAULT TRUE
);
```

El `BloqueoManualRule` consulta esta tabla al inicio de la cadena. Si existe
un bloqueo activo para el sector o su zona, emite `ABORT_ALL` y corta la
evaluación.

### 6. Trigger del motor: reactivo + proactivo

El motor se evalúa en dos caminos:

- **Reactivo**: en cada ingesta de telemetría MQTT (`MqttTelemetryReceiver` →
  `NurseryService` → `RuleOrchestrator.evaluate()`). Es el flujo actual.
- **Proactivo** (`@Scheduled`): para reglas que no dependen de telemetría
  fresca, como `MediasombraRule` (plan de rustificación por día) y
  `ClimaOverrideRule` (reevaluación periódica del forecast).

> **Pregunta abierta para debate**: ¿Ambos triggers son necesarios desde el
> inicio, o el proactivo se agrega después? Ver sección Open Questions.

### 7. Paquete y ubicación

```
backend/src/main/java/com/yerbanalytics/backend/
  engine/
    Rule.java                   (interfaz)
    RuleAction.java             (record)
    RuleContext.java            (record)
    RuleOrchestrator.java       (Spring @Service)
    ActionExecutor.java         (Spring @Service)
    rules/
      BloqueoManualRule.java    (@Component)
      StaleSensorRule.java      (@Component)
      DosisLimiteRule.java      (@Component)
      ClimaOverrideRule.java    (@Component)
      RiegoRule.java            (@Component)
      InsumoRule.java           (@Component)
      MediasombraRule.java      (@Component)
      SeguimientoRule.java      (@Component)
  weather/
    WeatherService.java         (@Service)
    WeatherForecast.java        (record)
```

## Mapeo de reglas a releases

El orden de implementación de las reglas sigue el orden de las releases del
proyecto. A criterio del Product Owner, las reglas deben incorporarse en la
misma secuencia en que sus historias de usuario entran al producto:

| Fase | Release | Reglas | Historias |
|------|---------|--------|-----------|
| **1 — Scaffold** | — | Extraer interfaz `Rule`, `RuleContext`, `RuleOrchestrator`, `ActionExecutor`. Migrar la lógica inline de `updateTelemetry()` sin cambiar comportamiento. | — |
| **2 — R1** | Monitoreo y Visualización Base | `StaleSensorRule` | HU-02 CA-03/04 |
| **3 — R2** | Diagnóstico Inteligente y Alertas | `ClimaOverrideRule` + `WeatherService` | HU-09, HU-10 |
| **4 — R3** | Automatización Agronómica | `RiegoRule`, `InsumoRule`, `MediasombraRule`, `BloqueoManualRule`, `DosisLimiteRule` | HU-06, HU-07, HU-08, HU-15, HU-19 |
| **5 — R4** | Trazabilidad y Operación Offline | `SeguimientoRule` (refactor del scheduler actual) | HU-11, HU-12 |

## Open Questions

### 1. Granularidad del ciclo de evaluación

¿El motor corre **solo por cada paquete de telemetría** (reactivo, como hoy)
o también con un **scheduler periódico** (proactivo, ej. cada 30 s para
reevaluar clima y rustificación aunque no haya telemetría nueva)?

**Recomendación**: ambos. El receptor MQTT dispara la evaluación reactiva
(como hoy), y un `@Scheduled` complementario evalúa reglas que no dependen
de telemetría fresca (mediasombra por plan de días, reevaluación de clima).

### 2. Alcance incremental

Las reglas se implementan siguiendo el orden de releases del producto (ver
tabla en "Mapeo de reglas a releases"). De este modo cada release incorpora
sus reglas correspondientes sin adelantar lógica de versiones futuras.

## Risks / Trade-offs

- **Riesgo**: la extracción introduce regresión en el comportamiento actual de
  riego/insumo. **Mitigación**: la fase 1 es un refactor puro (misma lógica,
  misma salida) con test de integración antes/después.
- **Trade-off**: reglas en Java puro vs. motor genérico (Drools). Aceptado:
  el dominio tiene ~8 reglas y el equipo no tiene experiencia con DSLs de
  reglas.
- **Trade-off**: `WeatherService` agrega una dependencia externa con latencia
  variable. Aceptado: el fallback a `null` garantiza que el motor nunca se
  bloquea por la API climática.
- **Riesgo**: la tabla `bloqueo_manual` no existe y requiere migración de
  esquema. **Mitigación**: se agrega como script DDL (`schema.sql`) consistente
  con el patrón existente.
