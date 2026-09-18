# rustificacion-mediasombra

> **Estado de implementación:** ✅ Implementado completamente.
> Clase: `MediasombraRule` (prioridad 12). Evalua el plan de `RustificacionEtapaEntity` y el índice UV del forecast. `ActionExecutor` maneja `MOVER_MEDIASOMBRA` parseando el patrón `[apertura=N]` y publica el comando MQTT. El Watchdog (`NurseryWatchdog`) dispara la regla en modo proactivo.

## Purpose

Control gradual de la mediasombra para ejecutar el plan de rustificación (HU-08).
La `MediasombraRule` evalúa el día actual del ciclo de siembra contra el plan de
etapas configurado por el agrónomo, y ajusta la apertura del actuador de
mediasombra. También responde a picos de radiación UV mediante el pronóstico
climático, priorizando la protección de los plantines sobre el plan.

## Requirements

### Requirement: Seguimiento del plan de rustificación por etapas

La `MediasombraRule` SHALL evaluar el día actual del ciclo de siembra (calculado
desde `yerbanalytics.nursery.fecha-siembra-iso`) contra el plan de etapas
persistido (`RustificacionEtapaEntity`). Cuando la apertura actual del sector
difiera de la apertura prescrita para la etapa del día, SHALL emitir
`MOVER_MEDIASOMBRA` con el porcentaje objetivo.

#### Scenario: Apertura correcta para la etapa actual

- **WHEN** la apertura actual del sector coincide con la apertura prescrita para
  el día del ciclo
- **THEN** la regla emite `NOOP_INFO` sin cambiar el actuador

#### Scenario: Apertura incorrecta para la etapa actual

- **WHEN** la apertura actual del sector difiere de la apertura prescrita para el
  día del ciclo
- **THEN** la regla emite `MOVER_MEDIASOMBRA` con el porcentaje objetivo de la etapa

#### Scenario: Día fuera del rango del plan

- **WHEN** el día del ciclo no cae dentro de ninguna etapa del plan configurado
- **THEN** la regla emite `NOOP_INFO` indicando que el día está fuera del rango del
  plan sin modificar el actuador

### Requirement: Fecha de siembra obligatoria para el plan

Si la propiedad `yerbanalytics.nursery.fecha-siembra-iso` no está configurada, la
regla SHALL omitir el plan de rustificación y emitir `NOOP_INFO`. El plan requiere
saber desde cuándo se cuenta el ciclo de días.

#### Scenario: Sin fecha de siembra

- **WHEN** `yerbanalytics.nursery.fecha-siembra-iso` es vacía o no está definida
- **THEN** la regla emite `NOOP_INFO` indicando que el plan fue omitido

### Requirement: Protección ante pico UV con precedencia sobre el plan

Si el pronóstico climático indica un índice UV ≥ umbral configurado
(`uv-umbral`, default 7.0), la regla SHALL ajustar la apertura a la posición
protectora (máximo 30%, limitado por `mediasombraAperturaMaxPct`), con
precedencia sobre el plan de rustificación.

#### Scenario: Pico UV activa posición protectora por encima del plan

- **WHEN** el índice UV supera el umbral y la etapa actual del plan prescribe más
  del 30% de apertura
- **THEN** la regla ajusta la mediasombra al 30% protector, ignorando el plan

#### Scenario: Pico UV pero mediasombra ya protegida

- **WHEN** el índice UV supera el umbral y la apertura actual ya es ≤ porcentaje
  protector
- **THEN** la regla emite `NOOP_INFO` sin mover el actuador

### Requirement: Respeto del límite máximo de apertura configurado

La `MediasombraRule` SHALL respetar siempre el límite `mediasombraAperturaMaxPct`
definido en `ConfiguracionOperativaEntity`: la apertura objetivo SHALL ser
`min(prescrita, mediasombraAperturaMaxPct)`.

#### Scenario: Plan prescribe más del límite máximo

- **WHEN** la etapa prescribe 80% de apertura pero `mediasombraAperturaMaxPct` es 70%
- **THEN** el objetivo es 70% (el límite operativo no puede superarse)

### Requirement: Materialización del movimiento de mediasombra

El `ActionExecutor` SHALL extraer el porcentaje objetivo del motivo de la acción
`MOVER_MEDIASOMBRA` (patrón `[apertura=N]`), actualizar el campo `actuadorShade`
del `SectorEntity` y publicar el comando MQTT `{"actuador":"shade","accion":"SET",
"parametros":{"targetPct":N}}` al tópico de comando del sector.

#### Scenario: Comando MQTT para mover mediasombra

- **WHEN** `MediasombraRule` emite `MOVER_MEDIASOMBRA` con apertura objetivo 45%
- **THEN** el `ActionExecutor` publica `{"commandId":"uuid","actuador":"shade",
  "accion":"SET","parametros":{"targetPct":45}}` al tópico de comando del sector
  y actualiza `actuadorShade = 45` en la BD

#### Scenario: Motivo sin patrón de apertura — fallback al valor actual

- **WHEN** el motivo de `MOVER_MEDIASOMBRA` no contiene `[apertura=N]`
- **THEN** el `ActionExecutor` usa el valor actual de `actuadorShade` como fallback

### Requirement: Trigger vía Watchdog (sin telemetría)

Dado que el plan de rustificación avanza por días calendario, la `MediasombraRule`
SHALL poder ser disparada por el Watchdog proactivo aunque no haya llegado
telemetría reciente de ese sector. El Watchdog SHALL iterar todos los sectores
periódicamente para verificar cambios de etapa del plan.

#### Scenario: Cambio de etapa al inicio del día sin telemetría

- **WHEN** comienza el día 8 del ciclo y el sector no ha recibido telemetría reciente
- **THEN** el Watchdog evalúa el sector, `MediasombraRule` detecta el cambio de etapa
  y emite `MOVER_MEDIASOMBRA` con la nueva apertura prescrita

### Requirement: Prioridad en el pipeline

`MediasombraRule` SHALL ejecutarse con prioridad 12 (ejecutora, después de
`RiegoRule` y `InsumoRule`). Su evaluación SHALL ser independiente de las reglas de
riego: un sector puede recibir riego y ajuste de mediasombra en el mismo ciclo.
