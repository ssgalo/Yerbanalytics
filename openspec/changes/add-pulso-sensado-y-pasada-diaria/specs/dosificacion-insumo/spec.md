## MODIFIED Requirements

### Requirement: Activación por diagnóstico IA crítico con confianza suficiente

La `InsumoRule` SHALL declarar que usa el diagnóstico de IA, así que sólo se evalúa en un pulso y con un diagnóstico
nuevo del sector (ver `motor-reglas`). Cuando se evalúa, SHALL activar la bomba peristáltica únicamente cuando el
sector está en estado `"critical"` Y la confianza del diagnóstico IA supera el umbral del 85%
(HU-07 CA-01/02). Si el estado no es crítico, o si la confianza está por debajo
del umbral, SHALL emitir `NOOP_INFO` con el motivo específico.
(Previously: se evaluaba en cada lectura, con el diagnóstico vigente del sector, fuera nuevo o no.)

#### Scenario: Dosificación autorizada

- **WHEN** en un pulso el sector tiene un diagnóstico nuevo, está en estado `"critical"` y la confianza del
  diagnóstico es ≥ 85%
- **THEN** `InsumoRule` emite `ACTIVAR_BOMBA` y el `ActionExecutor` activa la bomba
  peristáltica, registra la dosificación en el historial y publica el comando MQTT

#### Scenario: Estado no crítico — no se dosifica

- **WHEN** el sector tiene un diagnóstico nuevo y está en estado `"warn"` o `"ok"`
- **THEN** `InsumoRule` emite `NOOP_INFO` indicando que la dosificación solo se
  activa en estado crítico

#### Scenario: Confianza insuficiente

- **WHEN** el sector tiene un diagnóstico nuevo, está en estado `"critical"` pero la confianza del diagnóstico
  es < 85%
- **THEN** `InsumoRule` emite `NOOP_INFO` indicando la confianza actual y el umbral
  requerido; no se activa la bomba

#### Scenario: Sin diagnóstico IA disponible

- **WHEN** el sector no tiene diagnóstico (`diagnosisConf` es `null`)
- **THEN** `InsumoRule` no se evalúa por falta de diagnóstico nuevo y figura omitida en la traza

#### Scenario: Diagnóstico ya usado

- **GIVEN** que el diagnóstico del sector ya se usó en un pulso anterior
- **WHEN** llega el pulso siguiente con el sector en estado `"critical"`
- **THEN** `InsumoRule` no se evalúa y no se activa la bomba
