# alertas-inteligentes

> **Estado de implementación:**
> - ✅ **Frontend:** Tipo `Alert` definido en `domain.ts`, `AlertsDropdown` en la topbar (campana + panel desplegable + badge de no leídas), alertas generadas en el mock (`generators.ts`).
> - ❌ **Backend REST:** No existe `AlertaService` ni `AlertaController`. Los endpoints `GET/PATCH /api/alertas` y la generación de alertas persistidas desde el `ActionExecutor` son **pendientes de implementación**.
> - ❌ **Generación desde el motor:** El `ActionExecutor` aún no emite alertas estructuradas; el historial de inacción existe pero no hay una entidad de alerta separada.

## Purpose

Sistema de alertas clasificadas por severidad que comunica al productor anomalías
críticas y decisiones del motor sin necesidad de recorrer el vivero (HU-10).
Las alertas son generadas por el `ActionExecutor` cuando el motor emite acciones
de alto impacto o detecta condiciones de riesgo, y se exponen en el frontend como
notificaciones con badge de conteo y listado expandible.

## Requirements

### Requirement: Generación de alertas por el motor de reglas

El `ActionExecutor` SHALL generar una alerta cada vez que ocurre una de las
siguientes condiciones:

- **CRÍTICA**: nodo sensor sin reportar (telemetría stale) → `StaleSensorRule`.
- **CRÍTICA**: dosificación de insumo activada → `InsumoRule`.
- **ALTA**: riego autónomo activado → `RiegoRule`.
- **ALTA**: mediasombra movida por pico UV → `MediasombraRule`.
- **MEDIA**: riego pospuesto por lluvia inminente → `ClimaOverrideRule`.
- **INFO**: bloqueo manual activo impide actuación → `BloqueoManualRule`.

#### Scenario: Alerta crítica por insumo dosificado

- **WHEN** `ActionExecutor` ejecuta `ACTIVAR_BOMBA` para un sector
- **THEN** se genera una alerta de severidad `CRÍTICA` con el sector, el estado
  diagnosticado, la confianza del modelo y el timestamp

#### Scenario: Alerta alta por riego autónomo

- **WHEN** `ActionExecutor` ejecuta `ACTIVAR_VALVULA` para un sector
- **THEN** se genera una alerta de severidad `ALTA` indicando el sector y la
  humedad desencadenante

#### Scenario: Alerta media por riego pospuesto

- **WHEN** `ClimaOverrideRule` emite `POSTPONE_RIEGO`
- **THEN** se genera una alerta de severidad `MEDIA` informando la probabilidad de
  lluvia y el sector afectado

### Requirement: Clasificación por severidad

Cada alerta SHALL tener asignada una severidad: `CRÍTICA`, `ALTA`, `MEDIA` o `INFO`.
La severidad SHALL determinar el color y el badge visual en el frontend.

| Severidad | Color | Icono | Casos típicos |
|---|---|---|---|
| CRÍTICA | Rojo | 🔴 | Nodo caído, dosificación de insumo |
| ALTA | Naranja | 🟠 | Riego autónomo, protección UV |
| MEDIA | Amarillo | 🟡 | Riego pospuesto, sensor degradado |
| INFO | Azul | 🔵 | Bloqueo manual, inacción documentada |

#### Scenario: Badge de conteo por severidad

- **WHEN** hay 3 alertas críticas y 2 alertas altas sin leer
- **THEN** el badge del ícono de alertas en el header muestra el conteo total (5)
  con el color de la severidad más alta presente

### Requirement: Listado de alertas recientes

El sistema SHALL exponer las alertas recientes (últimas 24 h, configurable)
ordenadas del más reciente al más antiguo. Cada alerta SHALL incluir: sector,
zona, severidad, mensaje descriptivo, timestamp y si fue leída.

#### Scenario: Panel de alertas

- **WHEN** el productor abre el panel de alertas
- **THEN** ve el listado ordenado por timestamp descendente, con el color de
  severidad de cada entrada

#### Scenario: Sin alertas recientes

- **WHEN** no hubo eventos de alerta en el periodo configurado
- **THEN** el panel muestra un mensaje de "sin alertas recientes"

### Requirement: Marcado como leída

El usuario SHALL poder marcar una alerta como leída, lo que la elimina del badge
de conteo pero la conserva en el historial.

#### Scenario: Marcar como leída

- **WHEN** el usuario marca una alerta como leída
- **THEN** el badge se reduce en uno y la alerta se conserva visible en el listado
  con indicación de "leída"

### Requirement: Endpoint de alertas

El backend SHALL exponer:
- `GET /api/alertas` — listar alertas recientes (filtros opcionales: `sector`,
  `zona`, `severidad`, `leida`)
- `PATCH /api/alertas/{id}/leida` — marcar una alerta como leída

#### Scenario: Consulta de alertas no leídas

- **WHEN** se invoca `GET /api/alertas?leida=false`
- **THEN** devuelve solo las alertas sin leer ordenadas por timestamp descendente

### Requirement: Origen de datos por entorno

El frontend SHALL obtener las alertas a través de `DataRepository.getAlertas()`,
sin conocer el origen concreto (mock o backend HTTP).

#### Scenario: Modo backend

- **WHEN** `VITE_DATA_SOURCE=http`
- **THEN** las alertas se obtienen de `GET {VITE_API_BASE_URL}/alertas`

#### Scenario: Modo mock

- **WHEN** `VITE_DATA_SOURCE` no está definida o vale `mock`
- **THEN** las alertas provienen del mock determinístico
