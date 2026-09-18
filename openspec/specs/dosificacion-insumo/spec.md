# dosificacion-insumo

> **Estado de implementación:** ✅ Implementado completamente.
> Clases: `InsumoRule` (prioridad 11, gate confianza ≥ 85%), `DosisLimiteRule` (prioridad 5, bloqueo por historial 24h). `ActionExecutor` maneja `ACTIVAR_BOMBA` y publica el comando MQTT.

## Purpose

Dosificación automática de pesticidas y nutrientes sobre el sector comprometido
(HU-07). El motor de reglas activa la bomba peristáltica cuando el diagnóstico IA
confirma un estado crítico con confianza suficiente, sujeto al límite diario de
dosificación configurado por el agrónomo.

## Requirements

### Requirement: Activación por diagnóstico IA crítico con confianza suficiente

La `InsumoRule` SHALL activar la bomba peristáltica únicamente cuando el sector está
en estado `"critical"` Y la confianza del diagnóstico IA supera el umbral del 85%
(HU-07 CA-01/02). Si el estado no es crítico, o si la confianza está por debajo
del umbral, SHALL emitir `NOOP_INFO` con el motivo específico.

#### Scenario: Dosificación autorizada

- **WHEN** el sector está en estado `"critical"` y la confianza del diagnóstico
  es ≥ 85%
- **THEN** `InsumoRule` emite `ACTIVAR_BOMBA` y el `ActionExecutor` activa la bomba
  peristáltica, registra la dosificación en el historial y publica el comando MQTT

#### Scenario: Estado no crítico — no se dosifica

- **WHEN** el sector está en estado `"warn"` o `"ok"`
- **THEN** `InsumoRule` emite `NOOP_INFO` indicando que la dosificación solo se
  activa en estado crítico

#### Scenario: Confianza insuficiente

- **WHEN** el sector está en estado `"critical"` pero la confianza del diagnóstico
  es < 85%
- **THEN** `InsumoRule` emite `NOOP_INFO` indicando la confianza actual y el umbral
  requerido; no se activa la bomba

#### Scenario: Sin diagnóstico IA disponible

- **WHEN** el sector no tiene un diagnóstico reciente (`diagnosisConf` es `null`)
- **THEN** `InsumoRule` emite `NOOP_INFO` indicando que no hay confianza disponible
  para evaluar la dosificación

### Requirement: Límite diario de dosificación (HU-07 CA-03)

La `DosisLimiteRule` SHALL verificar que el sector no haya recibido ya una
dosificación en las últimas 24 horas antes de permitir que `InsumoRule` actúe.
Si el límite fue alcanzado, SHALL emitir `ABORT_INSUMO` (bloqueante). El límite
diario máximo SHALL provenir de `ConfiguracionOperativaEntity.insumoDosisMax24hMl`.

#### Scenario: Límite diario alcanzado

- **WHEN** el historial del sector registra al menos 1 evento de tipo `"Insumo"`
  en las últimas 24 horas
- **THEN** `DosisLimiteRule` emite `ABORT_INSUMO` y `InsumoRule` no se evalúa en
  ese ciclo

#### Scenario: Límite diario disponible

- **WHEN** el historial del sector no registra eventos de `"Insumo"` en las últimas
  24 horas
- **THEN** `DosisLimiteRule` emite `NOOP_INFO` y la cadena continúa hasta `InsumoRule`

#### Scenario: Sin configuración disponible — fail-open

- **WHEN** no existe una `ConfiguracionOperativaEntity` disponible en el contexto
- **THEN** `DosisLimiteRule` emite `NOOP_INFO` sin bloquear la dosificación
  (fail-open para no paralizan el sistema ante falta de configuración)

### Requirement: No se duplica el historial en el mismo estado

El `ActionExecutor` SHALL persistir el evento de dosificación en `historial_evento`
únicamente en la transición de estado (de "no dosificando" a "Dosificando"). Si la
bomba ya estaba activa en el ciclo anterior, no SHALL generarse un nuevo evento.

#### Scenario: Transición a Dosificando registra evento

- **WHEN** el sector pasa de estado de bomba `null` o `"Inactiva"` a `"Dosificando"`
- **THEN** se persiste un evento de historial con tipo `"Insumo"`, el diagnóstico
  que lo originó y la confianza del modelo

#### Scenario: Sin evento duplicado

- **WHEN** la bomba ya estaba en `"Dosificando"` y sigue en ese estado
- **THEN** no se genera un nuevo evento de historial para ese ciclo

### Requirement: Comando MQTT al actuador

El `ActionExecutor` SHALL publicar el comando `{"actuador":"pump","accion":"ON"}`
al tópico `nursery/zone/{zonaId}/sector/{sectorId}/command` (QoS 2) cuando activa
la bomba peristáltica.

#### Scenario: Comando publicado al activar bomba

- **WHEN** `InsumoRule` emite `ACTIVAR_BOMBA` y el sector transiciona
- **THEN** el `ActionExecutor` publica el payload `{"commandId":"uuid","actuador":
  "pump","accion":"ON","parametros":{}}` al tópico de comando del sector

### Requirement: Prioridad relativa en el pipeline

`DosisLimiteRule` SHALL ejecutarse con prioridad 5 (antes de `RiegoRule` y
`InsumoRule`), y `InsumoRule` con prioridad 11 (después de `RiegoRule`).
Este orden SHALL garantizar que el guard de límite diario se evalúe antes que
la regla ejecutora.
