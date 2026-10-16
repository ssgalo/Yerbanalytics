# Design: add-full-demo-mode

## 0. Flujo General del Sistema

```
========================================================================================
                                MODO SIMULADO / FULL DEMO
========================================================================================

 [Operador en Simulador]
        │
        ├─ 1. Configura Clima Ficticio (ej: 0 mm lluvia, 32°C, UV 80%)
        │     ──POST /backend/api/demo/weather──▶ [WeatherService] (in-memory mock)
        │
        ├─ 2. Configura cada Regla de forma INDIVIDUAL:
        │     - Feature Flag de Activación: enabled (ON / OFF)
        │     - Flags de Cooldowns y Variables de Bypass independientes:
        │       * cooldownMinutes (0 = sin espera, o N minutos)
        │       * bypassVariables (ej: bypassRiegoEnCurso, bypassVentanaHoraria)
        │     ──PUT /backend/api/demo/rules/{ruleName} {enabled: true, cooldownMinutes: 0, bypassVariables: {...}}
        │
        ├─ 3. Ingresa Métricas Ficticias de Sensores (humSus: 25%, temp: 31°C)
        │
        └─ 4. Presiona Botón "LEER AHORA"
               │
               ▼
        ──POST /backend/api/pulsos {ejecutarMotor: true}──▶ [PulsoSensadoService]
                                                                 │
                                                                 ▼ publica
                                              MQTT: nursery/zone/{z}/command (LEER_AHORA)
                                                                 │
        [Simulador MQTT Client] ◀────────────────────────────────┘
               │
               ▼ responde con métricas ficticias ingresadas
        MQTT: nursery/zone/{z}/telemetry
               │
               ▼ ingesta
        [NurseryService.procesarLecturaZona]
               │
               ├─ Actualiza lectura de Zona y Sectores
               │
               ├─ Consulta Clima ──▶ [WeatherService]
               │                      - En modo demo: retorna clima ficticio inyectado
               │                      - En producción: consulta Open-Meteo
               │
               ├─ Ejecuta Reglas ──▶ [RuleOrchestrator] (MISMAS CLASES DE REGLAS)
               │                      │
               │                      ├─ Si la regla está apagada (enabled=false):
               │                      │   ──▶ OMITIDA_DESHABILITADA en traza
               │                      │
               │                      ├─ Si la regla está encendida:
               │                      │   ──▶ Consulta sus flags de cooldowns y variables de bypass:
               │                      │       - En producción: usa los CDs y restricciones configuradas por el usuario
               │                      │       - En demo: usa sus valores específicos (ej: CD = 0 min, bypass = true)
               │                      │
               │                      └─ Si las condiciones aplican:
               │                          ──▶ Emite Acciones (ACTIVAR_VALVULA, etc.)
               │
               ├─ Registra en HISTORIAL ──▶ [HistorialService] (¡AFECTA HISTORIAL REAL!)
               │
               └─ Despacha Actuación ──▶ [ActionExecutor]
                                               │
                                               ▼ publica
                                    MQTT: nursery/zone/{z}/sector/{s}/command
                                               │
                                               ▼
                                      [ESP32 Hardware Real]
                                   (abre válvula / mueve sombra)
```

---

## 1. Principio Fundamental: Misma Base de Código y Configuración Granular por Regla

A diferencia de un diseño con clases duplicadas (como `DemoRule` vs `ProductionRule`), este diseño
establece que **las reglas de negocio tienen exactamente el mismo código en producción y en demo**.

Cada regla cuenta con:
1. **Feature Flag de activación (`enabled: true/false`)**:
   - Permite encender o apagar la regla a merced.
   - Si está apagada (`false`), el motor la saltea y la marca en la traza como `OMITIDA_DESHABILITADA`.
2. **Múltiples Flags de Cooldowns y Variables de Bypass independientes**:
   - **Cada regla puede tener más de un cooldown o variable que se quiera bypasear en modo demo**.
   - Ejemplos de variables y cooldowns por regla:
     - `CicloLecturaRiegoRule` (R-01):
       - `cooldownCicloMinutos`: cooldown del ciclo de lectura (producción: 240 min; demo: configurable, default 0).
       - `bypassRiegoEnCurso`: flag para permitir regar incluso si hay una válvula actualmente abierta en el sector.
     - `DeficitCriticoRule` (R-02):
       - `cooldownCriticoHoras`: tiempo entre riegos críticos sucesivos (producción: 12 h; demo: default 0).
     - `PausaTrasAplicacionRule` (R-06):
       - `cooldownPausaInsumoHoras`: pausa tras aplicar insumos (producción: 4 h; demo: default 0).
     - `FueraDeVentanaRiegoRule` (R-05):
       - `bypassVentanaHoraria`: flag para permitir riego fuera de la ventana de 06:00 a 18:00 en demos nocturnas.
     - `DailyDoseLimitRule` (R-07):
       - `bypassDosisMaxima`: flag para permitir exceder el volumen máximo diario acumulado durante demostraciones.
     - `ManualLockRule` (R-00):
       - `bypassBloqueoManual`: flag para forzar actuación aún con bloqueo manual activo en la zona o sector.
   - **En producción**: cada regla utiliza estrictamente sus cooldowns y variables configuradas por el usuario / agrónomo.
   - **En modo demo**: cada regla admite sobreescribir sus cooldowns (default: `0 minutos`) y conmutar sus variables de bypass independientemente.

---

## 2. Feature Flags y Configuración de Modos (Spring Boot + Docker)

### 2.1 Modos de Operación

| Modo | Configuración | Comportamiento |
|---|---|---|
| **REAL** (default) | `yerbanalytics.mode=REAL` o `yerbanalytics.demo.enabled=false` | Comportamiento productivo estándar: cada regla opera con sus cooldowns y restricciones establecidas por el usuario, las lecturas se programan periódicamente según `intervaloSensadoMinutos`, el clima proviene de Open-Meteo, todas las reglas están encendidas y los endpoints `/api/demo/**` no existen (404). |
| **SIMULATED** | `yerbanalytics.mode=SIMULATED` o `yerbanalytics.demo.enabled=true` | Modo interactivo de demostración: se habilitan los flags para encender/apagar reglas y configurar cooldowns y variables de bypass independientes por regla (default 0), clima simulado en memoria y el botón "Leer ahora" en el simulador. |

### 2.2 Configuración en `application.properties`

```properties
# =============================================================================
# Modo de Operación y Feature Flag de Simulación / Demo
# =============================================================================
# Modo general: REAL | SIMULATED
yerbanalytics.mode=REAL

# Flag booleano directo
yerbanalytics.demo.enabled=false
```

### 2.3 Sobrescritura por Variables de Entorno (Docker / Docker Compose)

Spring Boot mapea automáticamente las variables de entorno:
- `YERBANALYTICS_MODE=SIMULATED`
- `YERBANALYTICS_DEMO_ENABLED=true`

En `docker-compose.yml`:
```yaml
environment:
  YERBANALYTICS_MODE: ${YERBANALYTICS_MODE:-REAL}
  YERBANALYTICS_DEMO_ENABLED: ${YERBANALYTICS_DEMO_ENABLED:-false}
```

---

## 3. Modelo de Control Individual de Reglas, Cooldowns Múltiples y Variables de Bypass

### 3.1 Estado Granular en `RuleOrchestrator`

El `RuleOrchestrator` mantiene en memoria la configuración granular de cada regla:

```java
package com.yerbanalytics.backend.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RuleOrchestrator {

    private final List<Rule> rules;
    private final CatalogoParametrosService parametros;
    private final TrazaEvaluacionStore trazaStore;

    // Estado granular por regla (ruleName -> RuleControlState)
    private final Map<String, RuleControlState> ruleStates = new ConcurrentHashMap<>();

    public record RuleControlState(
        boolean enabled,
        Integer cooldownMinutesOverride,
        Map<String, Object> bypassVariables
    ) {}

    public boolean isRuleEnabled(String ruleName) {
        RuleControlState state = ruleStates.get(ruleName);
        return state == null || state.enabled();
    }

    public void setRuleEnabled(String ruleName, boolean enabled) {
        ruleStates.compute(ruleName, (k, v) ->
            v == null
                ? new RuleControlState(enabled, null, Map.of())
                : new RuleControlState(enabled, v.cooldownMinutesOverride(), v.bypassVariables()));
    }

    public Integer getCooldownOverride(String ruleName) {
        RuleControlState state = ruleStates.get(ruleName);
        return state != null ? state.cooldownMinutesOverride() : null;
    }

    public void setCooldownOverride(String ruleName, Integer cooldownMinutes) {
        ruleStates.compute(ruleName, (k, v) ->
            v == null
                ? new RuleControlState(true, cooldownMinutes, Map.of())
                : new RuleControlState(v.enabled(), cooldownMinutes, v.bypassVariables()));
    }

    public Object getBypassVariable(String ruleName, String variableKey) {
        RuleControlState state = ruleStates.get(ruleName);
        return state != null && state.bypassVariables() != null
            ? state.bypassVariables().get(variableKey)
            : null;
    }

    public void setBypassVariable(String ruleName, String variableKey, Object value) {
        ruleStates.compute(ruleName, (k, v) -> {
            Map<String, Object> vars = new ConcurrentHashMap<>(v != null && v.bypassVariables() != null ? v.bypassVariables() : Map.of());
            vars.put(variableKey, value);
            return new RuleControlState(v != null ? v.enabled() : true, v != null ? v.cooldownMinutesOverride() : null, vars);
        });
    }

    public void resetAllCooldownsToZero() {
        for (Rule rule : rules) {
            setCooldownOverride(rule.name(), 0);
        }
    }
}
```

### 3.2 Trazabilidad de Reglas Apagadas

Durante la evaluación en `RuleOrchestrator`:
```java
for (Rule rule : rules) {
    RuleBranch branch = rule.branch();

    // 1. Verificación del flag de activación de la regla
    if (!isRuleEnabled(rule.name())) {
        log.debug("Sector {}: regla '{}' (rama {}) apagada por flag.", sectorId, rule.name(), branch);
        trazas.add(new TrazaRegla(rule.name(), branch, rule.priority(),
                EstadoRegla.OMITIDA_DESHABILITADA, List.of(), List.of(), "Regla apagada por flag"));
        continue;
    }

    // 2. Continúa con la evaluación normal...
}
```

En `EstadoRegla.java`:
```java
public enum EstadoRegla {
    EVALUADA,
    OMITIDA_RAMA_BLOQUEADA,
    OMITIDA_SIN_DIAGNOSTICO_NUEVO,
    OMITIDA_DESHABILITADA, // <--- Regla apagada manualmente por flag
    NO_ALCANZADA,
    ERROR
}
```

### 3.3 Resolución de Cooldowns y Variables de Bypass en `RuleContext`

En `RuleContext.java`, se proveen métodos de resolución que respetan el modo de operación:

```java
public record RuleContext(
    SectorEntity sector,
    ZonaEntity zona,
    EffectiveSpecs specs,
    List<Metric> metrics,
    ConfiguracionOperativaEntity config,
    String finalStatus,
    Instant now,
    WeatherForecast forecast,
    boolean bloqueoManualActivo,
    ContextoRiego riego,
    Map<String, Integer> cooldownOverrides,
    Map<String, Map<String, Object>> bypassVariables
) {
    /**
     * Retorna el cooldown efectivo para una regla específica en minutos.
     * Si existe un override en modo demo, se utiliza ese valor (ej: 0 minutos).
     * Si no, retorna el valor productivo por defecto configurado.
     */
    public double effectiveCooldownMinutes(String ruleName, double defaultProductionMinutes) {
        if (cooldownOverrides != null && cooldownOverrides.containsKey(ruleName)) {
            Integer override = cooldownOverrides.get(ruleName);
            if (override != null) {
                return override.doubleValue();
            }
        }
        return defaultProductionMinutes;
    }

    /**
     * Retorna si una variable o guarda específica de la regla fue bypaseada en demo.
     */
    public boolean isVariableBypassed(String ruleName, String variableKey, boolean defaultProductionValue) {
        if (bypassVariables != null && bypassVariables.containsKey(ruleName)) {
            Object val = bypassVariables.get(ruleName).get(variableKey);
            if (val instanceof Boolean b) {
                return b;
            }
        }
        return defaultProductionValue;
    }
}
```

### 3.4 Patrón de Implementación en Reglas con Múltiples Cooldowns o Variables

Cada regla calcula sus guardas usando los métodos de resolución del contexto:

1. **`CicloLecturaRiegoRule` (múltiples guardas: ciclo y riego en curso)**:
   ```java
   double cdMin = ctx.effectiveCooldownMinutes(NAME, minutosDeCicloConfigurado);
   boolean bypassEnCurso = ctx.isVariableBypassed(NAME, "bypassRiegoEnCurso", false);

   // Guarda 1: Cooldown de ciclo de lectura
   if (cdMin > 0 && minutosDesdeRiego <= cdMin && !deficitCritico) {
       return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, "Ya se regó en este ciclo."));
   }

   // Guarda 2: Riego en curso (bypaseable independientemente en demo)
   if (!bypassEnCurso && enCurso) {
       return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, "Riego en curso."));
   }
   ```

2. **`DeficitCriticoRule` (cooldown de 12 horas)**:
   ```java
   double cdHoras = ctx.effectiveCooldownMinutes(NAME, ev.numero(ParametrosRiego.EXCEPTUADO_BLOQUEO) * 60) / 60.0;
   if (cdHoras > 0 && horasDesdeUltimoCritico < cdHoras) {
       return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, "Límite crítico (" + cdHoras + " h)."));
   }
   ```

3. **`FueraDeVentanaRiegoRule` (variable de bypass de ventana)**:
   ```java
   boolean bypassVentana = ctx.isVariableBypassed(NAME, "bypassVentanaHoraria", false);
   if (!bypassVentana && fueraDeVentana) {
       return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, "Fuera de ventana de riego."));
   }
   ```

---

## 4. Simulación de la API Climática

`WeatherService` mantiene su interfaz existente `WeatherClient`:
- En producción: consulta la API externa Open-Meteo.
- En modo demo: admite inyectar en memoria un pronóstico ficticio (`setMockForecast`).
- El simulador permite ingresar temperatura exterior, probabilidad de lluvia (%) y precipitación (mm)
  para ejercitar de inmediato `PosponerPorLluviaRule` y `ShadingRule`.

---

## 5. Botón "Leer Ahora" y Ciclo Ciberfísico Completo

### 5.1 Comportamiento del Botón "Leer Ahora"

En **Modo Simulado**, el simulador web (`:5180`) muestra el botón **"Leer ahora"**.

1. **Sincronización de Clima**: Si se modificó el clima en el simulador, envía `POST /backend/api/demo/weather`.
2. **Disparo de Pulso Manual**: Invoca `POST /backend/api/pulsos` con `{ "ejecutarMotor": true }`.
3. **Petición MQTT del Backend**: `PulsoSensadoService` publica `LEER_AHORA` en `nursery/zone/{zonaId}/command`.
4. **Respuesta con Métricas Ficticias**: El simulador responde en `nursery/zone/{zonaId}/telemetry`.
5. **Evaluación de Reglas con Cooldowns y Variables Granulares**:
   - `NurseryService` procesa la lectura.
   - `RuleOrchestrator` evalúa sólo las reglas encendidas y aplicando los cooldowns y bypass variables configurados para cada regla.
   - **Persiste las acciones en el historial real (`historialService`)**.
6. **Actuación Física hacia el ESP32**: `ActionExecutor` publica la orden en `nursery/zone/{z}/sector/{s}/command`.

---

## 6. Endpoints REST de la API Demo (`/api/demo/**`)

Disponibles **únicamente** cuando `yerbanalytics.demo.enabled=true`.

### 6.1 Catálogo, Flags, Cooldowns y Variables de Bypass de Reglas

#### `GET /api/demo/rules`
Retorna todas las reglas con su flag de activación (`enabled`), su cooldown por defecto, cooldown configurado y mapa de variables de bypass.
```json
[
  {
    "name": "CicloLecturaRiegoRule",
    "label": "🔁 Un riego por ciclo de lectura",
    "branch": "RIEGO",
    "priority": 3,
    "enabled": true,
    "defaultCooldownMinutes": 240,
    "cooldownMinutes": 0,
    "bypassVariables": {
      "bypassRiegoEnCurso": true
    }
  },
  {
    "name": "DeficitCriticoRule",
    "label": "🚨 Déficit hídrico crítico (R-02)",
    "branch": "RIEGO",
    "priority": 4,
    "enabled": true,
    "defaultCooldownMinutes": 720,
    "cooldownMinutes": 0,
    "bypassVariables": {}
  },
  {
    "name": "FueraDeVentanaRiegoRule",
    "label": "🌙 Riego fuera de ventana horaria (R-05)",
    "branch": "RIEGO",
    "priority": 6,
    "enabled": true,
    "defaultCooldownMinutes": 0,
    "bypassVariables": {
      "bypassVentanaHoraria": true
    }
  }
]
```

#### `PUT /api/demo/rules/{ruleName}`
Actualiza el flag de encendido, cooldown y/o variables de bypass de esa regla.
```json
{
  "enabled": true,
  "cooldownMinutes": 0,
  "bypassVariables": {
    "bypassRiegoEnCurso": true
  }
}
```

#### `PUT /api/demo/rules/cooldowns/zero`
Atajo para poner el cooldown de todas las reglas en 0 simultáneamente.

---

## 7. Garantías de Cero Rastro y Desacoplamiento

1. **Sin clases duplicadas**: Todas las reglas son componentes Spring únicos.
2. **Cero impacto en Producción**: Con `yerbanalytics.demo.enabled=false`:
   - El mapa de overrides y bypass variables es nulo/inaccesible; cada regla usa sus parámetros y restricciones normales.
   - Todas las reglas están encendidas (`enabled = true`).
   - Los endpoints `/api/demo/**` no se registran en Spring (retornan 404).
   - No hay tablas demo en PostgreSQL ni rastro en base de datos.
