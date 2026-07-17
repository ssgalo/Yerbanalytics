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

### 1. Patrón: Rule como Strategy en un pipeline con corte por prioridad

Cada regla implementa una interfaz `Rule` con un método `evaluate(RuleContext)`
que devuelve una lista de `RuleAction`. El `RuleOrchestrator` las itera en
orden de prioridad y corta la cadena si una acción es bloqueante.

> **Ordenamiento explícito (Spring no lo hace por vos)**: Spring inyecta la
> `List<Rule>` en orden de declaración, **no** por `priority()`. El
> `RuleOrchestrator` debe ordenarla en su constructor con
> `Comparator.comparingInt(Rule::priority)`; sin eso, el corte de la cadena
> corre en orden arbitrario (bug silencioso). Se cubre con un test.

> **Precisión del patrón**: no es un Chain of Responsibility clásico (donde
> cada handler decide si delega al siguiente). Acá el orquestador posee el loop
> y corta desde afuera, más cercano a un **pipeline de estrategias con corte
> anticipado** (Specification). No cambia el diseño, solo el nombre correcto.

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
  `Lectura → Decisión → Acción` (HU-11 CA-01). **Esto incluye el Registro de Inacción:** si una regla aborta una acción (ej. "no regar por lluvia"), se persiste un evento informativo para que el usuario sepa *por qué* el sistema decidió no actuar.
- **Emisión de alertas** clasificadas por severidad (HU-10).
- **Actualización** de campos del `SectorEntity` (`actuador_valve`,
  `actuador_pump`, `actuador_shade`).

> **El canal backend ↔ actuador debe separarse por intereses (Separation of Concerns).**
> Para evitar bucles infinitos (ecos) y tener payloads limpios, usaremos tres tópicos:
> - **Ingesta:** `nursery/zone/{zonaId}/sector/{sectorId}/telemetry` (ESP32 publica, Backend suscribe).
> - **Comando:** `nursery/zone/{zonaId}/sector/{sectorId}/command` (Backend publica, ESP32 suscribe).
> - **Confirmación (Ack):** `nursery/zone/{zonaId}/sector/{sectorId}/ack` (ESP32 publica). El payload debe indicar obligatoriamente si pudo terminar o no (ej. `{"status": "SUCCESS"}` o `{"status": "ERROR"}`).
> - **QoS**: se recomienda **QoS 2** (exactly-once) para comandos de actuador,
>   porque una orden duplicada puede significar doble riego o **doble dosis de
>   químico**.
> - **Convivencia con el simulador**: el `ActionExecutor` debe usar un patrón Factory/Strategy para despachar comandos. En simulador escribe a la BD directo; en producción publica en MQTT.

### 4. WeatherService: cliente de API climática (HU-09)

- Consulta asíncrona con timeout de 10 s (HU-09 CA-01).
- Hasta 3 reintentos con exponential backoff si el servidor no responde en
  15 s (HU-09 CA-02).
- Si los reintentos fallan: `forecast = null`, se loguea un warning de
  degradación de servicio, y el motor opera solo con sensores (HU-09 CA-03).
- Cache local del último forecast válido para minimizar llamadas. **Debe ser
  thread-safe**: con el trigger reactivo (MQTT) y el proactivo (`@Scheduled`)
  corriendo en paralelo, una cache mutable sin sincronizar es una condición de
  carrera.

### 5. Configuración dinámica (Envars) e Idempotencia (Cooldowns)

Para proteger al hardware de inundaciones de comandos (flooding) si los sensores envían datos muy seguido, el motor debe aplicar "Cooldowns" o periodos de enfriamiento antes de repetir una orden sobre un mismo sector.

Para lograr flexibilidad sin necesidad de despliegues, estos tiempos críticos se configurarán mediante variables de entorno (que luego inyecta Spring vía `@Value`):
- `SENSOR_POLLING_INTERVAL`: Cada cuánto tiempo (en ms o minutos) el ESP32 envía telemetría.
- `ACTION_COOLDOWN_MINUTES`: Minutos mínimos que deben pasar antes de enviar el mismo comando (ej. riego) al mismo sector.

**Máquina de estados para los Cooldowns:**
1. **In-Flight Lock:** Cuando el Backend publica un comando, marca el sector como `ACTUANDO`. Las reglas ignoran nueva telemetría mientras esté en este estado para no duplicar comandos. Si se excede un *timeout* sin respuesta, se asume falla y se quita el lock.
2. **Evaluación de ACK:** Cuando el ESP32 publica en el tópico de `ack`:
   - Si dice `{"status": "SUCCESS"}`: Se quita el lock `ACTUANDO`, se actualiza el timestamp `ultimo_exito_ts = now()`, y **comienza a correr el `ACTION_COOLDOWN_MINUTES`**.
   - Si dice `{"status": "ERROR"}`: Se quita el lock `ACTUANDO` pero **no** se aplica el cooldown, permitiendo que el motor reintente la acción en el próximo ciclo de telemetría.

### 5. Tabla `bloqueo_manual` (HU-19)

El esquema se crea con el **patrón real del repo**: una entidad JPA que
Hibernate materializa vía `ddl-auto=update`. **No hay `schema.sql`** en el
proyecto; las 9 tablas nacen de sus entidades y el `data.sql` solo tiene
`INSERT`s de seed. Se agrega la entidad + su repository, como toda otra tabla.

```java
@Entity
@Table(name = "bloqueo_manual")
public class BloqueoManualEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sectorId;          // null = bloquea toda la zona
    private String zonaId;
    private String activadoPor;
    private Long activadoTs;
    private String motivo;
    private Boolean activo = Boolean.TRUE;
}
// + BloqueoManualRepository extends JpaRepository<BloqueoManualEntity, Long>
```

El `BloqueoManualRule` consulta este repository al inicio de la cadena. Si existe
un bloqueo activo para el sector o su zona, emite `ABORT_ALL` y corta la
evaluación.

### 6. Trigger del motor: reactivo + proactivo

El motor se evalúa en dos caminos:

- **Reactivo**: en cada ingesta de telemetría MQTT (`MqttTelemetryReceiver` →
  `NurseryService` → `RuleOrchestrator.evaluate()`). Es el flujo actual.
- **Proactivo (Watchdog)** (`@Scheduled`): Además de evaluar reglas independientes de telemetría (como el plan de días de la `MediasombraRule` y el pronóstico de la `ClimaOverrideRule`), el scheduler actúa como **Perro Guardián**. Itera periódicamente todos los sectores: si el hardware está apagado y no envía telemetría (flujo reactivo muerto), el scheduler dispara el motor para que el `StaleSensorRule` detecte la anomalía y emita una Alerta Crítica de nodo desconectado.

> **Pregunta abierta para debate**: ¿Ambos triggers son necesarios desde el
> inicio, o el proactivo se agrega después? **Resuelto**: Se necesitan ambos, el proactivo es vital por su rol de Watchdog.

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
  misma salida) con test de integración antes/después. El design debe mostrar la
  **nueva firma de `updateTelemetry()`** tras el refactor (qué queda síncrono y
  qué delega al orquestador) para que la extracción sea inequívoca.
- **Riesgo (seguridad)**: sin idempotencia, dos paquetes del mismo sector casi
  simultáneos (o un mensaje MQTT duplicado por QoS 1) pueden hacer que dos hilos
  evalúen y **ejecuten la misma acción dos veces** — en "inyectar insumo" es
  doble dosis. **Mitigación**: Implementar los cooldowns vía envars (`ACTION_COOLDOWN_MINUTES`), combinado con QoS 2 en
  el canal de comando y el tópico de `ack` para confirmar ejecución real.
- **Trade-off**: reglas en Java puro vs. motor genérico (Drools). Aceptado:
  el dominio tiene ~8 reglas y el equipo no tiene experiencia con DSLs de
  reglas.
- **Trade-off**: `WeatherService` agrega una dependencia externa con latencia
  variable. Aceptado: el fallback a `null` garantiza que el motor nunca se
  bloquea por la API climática.
- **Riesgo**: la tabla `bloqueo_manual` no existe. **Mitigación**: se agrega
  como **entidad JPA + repository** y Hibernate la crea vía `ddl-auto=update`,
  que es el patrón real del repo. (No hay `schema.sql`; el `data.sql` solo
  siembra datos.)
