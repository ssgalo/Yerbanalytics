# Tasks: add-rules-engine

## 1. Scaffold del motor (refactor puro)
- [ ] 1.1 Crear paquete `engine/` con interfaz `Rule`, records `RuleContext` y `RuleAction`
- [ ] 1.2 Crear `RuleOrchestrator` (itera reglas por prioridad, corta en bloqueantes)
- [ ] 1.3 Crear `ActionExecutor` (MQTT publish a actuadores, persistencia historial, alertas)
- [ ] 1.4 Extraer lógica de riego de `NurseryService.updateTelemetry()` → `RiegoRule`
- [ ] 1.5 Extraer lógica de insumo de `NurseryService.updateTelemetry()` → `InsumoRule`
- [ ] 1.6 Test de regresión: el `updateTelemetry()` refactorizado produce el mismo resultado

## 2. Reglas R1 — Monitoreo y Visualización Base
- [ ] 2.1 `StaleSensorRule`: evalúa antigüedad de telemetría, emite `ABORT_RIEGO` si > umbral
- [ ] 2.2 Test unitario de `StaleSensorRule` con contextos fabricados

## 3. Reglas R2 — Diagnóstico Inteligente y Alertas
- [ ] 3.1 `WeatherService`: cliente asíncrono de API climática (timeout 10 s, retry x3 backoff, fallback null)
- [ ] 3.2 Record `WeatherForecast` (probabilidad lluvia, índice UV, timestamp)
- [ ] 3.3 `ClimaOverrideRule`: si lluvia inminente supera umbral → `POSTPONE_RIEGO`
- [ ] 3.4 Test unitario de `ClimaOverrideRule` (con forecast / sin forecast / con lluvia / sin lluvia)

## 4. Reglas R3 — Automatización Agronómica
- [ ] 4.1 Tabla `bloqueo_manual` en `schema.sql` + entidad JPA + repository
- [ ] 4.2 `BloqueoManualRule`: consulta bloqueos activos → `ABORT_ALL` si existe
- [ ] 4.3 `DosisLimiteRule`: consulta historial 24 h del sector → `BLOQUEAR_DOSIFICACION` si supera máx
- [ ] 4.4 `MediasombraRule`: evalúa día del plan de rustificación y/o pico UV → `MOVER_MEDIASOMBRA`
- [ ] 4.5 Enriquecer `RiegoRule` con guard de límites operativos (tiempo máx, volumen diario)
- [ ] 4.6 Enriquecer `InsumoRule` con guard de confidence score ≥ 85% (HU-07 CA-01/02)
- [ ] 4.7 Tests unitarios de cada regla de R3

## 5. Reglas R4 — Trazabilidad y Operación Offline
- [ ] 5.1 `SeguimientoRule`: refactor del scheduler `evaluarSeguimiento()` como Rule
- [ ] 5.2 Test unitario de `SeguimientoRule`

## 6. Integración y cierre
- [ ] 6.1 Conectar `RuleOrchestrator` al flujo de `MqttTelemetryReceiver` → `NurseryService`
- [ ] 6.2 Scheduler proactivo (`@Scheduled`) para reglas independientes de telemetría
- [ ] 6.3 Test de integración end-to-end del ciclo telemetría → reglas → acciones → historial
- [ ] 6.4 Documentar en `CLAUDE.md` §6 la sección del motor de reglas
