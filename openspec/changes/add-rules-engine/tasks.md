# Tasks: add-rules-engine

## 1. Scaffold del motor (refactor puro)
- [x] 1.1 Crear paquete `engine/` con interfaz `Rule`, records `RuleContext` y `RuleAction`
- [x] 1.2 Crear `RuleOrchestrator` (ordena las reglas en el constructor con `Comparator.comparingInt(Rule::priority)` —Spring no las ordena solo—, itera por prioridad, corta en bloqueantes)
- [x] 1.3 Crear `ActionExecutor` (Soporte MQTT telemetry/command/ack, persistencia historial con Registro de Inacción, alertas)
- [x] 1.4 Extraer lógica de riego de `NurseryService.updateTelemetry()` → `RiegoRule`
- [x] 1.5 Extraer lógica de insumo de `NurseryService.updateTelemetry()` → `InsumoRule`
- [x] 1.6 Test de regresión: el `updateTelemetry()` refactorizado produce el mismo resultado
- [x] 1.7 Test: el `RuleOrchestrator` evalúa las reglas en orden de prioridad (verifica el ordenamiento explícito)
- [x] 1.8 Agregar variables de entorno para `SENSOR_POLLING_INTERVAL` y `ACTION_COOLDOWN_MINUTES` en `application.yml`

## 2. Reglas R1 — Monitoreo y Visualización Base
- [ ] 2.1 `StaleSensorRule`: evalúa antigüedad de telemetría, emite `ABORT_RIEGO` si > umbral
- [ ] 2.2 Test unitario de `StaleSensorRule` con contextos fabricados

## 3. Reglas R2 — Diagnóstico Inteligente y Alertas
- [ ] 3.1 `WeatherService`: cliente asíncrono de API climática (timeout 10 s, retry x3 backoff, fallback null)
- [ ] 3.2 Record `WeatherForecast` (probabilidad lluvia, índice UV, timestamp)
- [ ] 3.3 `ClimaOverrideRule`: si lluvia inminente supera umbral → `POSTPONE_RIEGO`
- [ ] 3.4 Test unitario de `ClimaOverrideRule` (con forecast / sin forecast / con lluvia / sin lluvia)

## 4. Reglas R3 — Automatización Agronómica
- [ ] 4.1 Entidad JPA `BloqueoManualEntity` + `BloqueoManualRepository` (Hibernate crea la tabla vía `ddl-auto`; no hay `schema.sql`)
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
- [ ] 6.2 Scheduler proactivo (`@Scheduled`) como Watchdog (itera sectores evaluando antigüedad para disparar `StaleSensorRule` si el hardware cae) y evaluar reglas sin telemetría
- [ ] 6.3 Test de integración end-to-end del ciclo telemetría → reglas → acciones → historial
- [ ] 6.4 Documentar en `CLAUDE.md` §6 la sección del motor de reglas
- [ ] 6.5 Construir el canal de comando backend ↔ actuador con separación estricta: tópicos `telemetry`, `command` y `ack`, usando QoS 2 para comandos. Implementar patrón Factory para convivencia con el simulador
- [ ] 6.6 Estrategia de idempotencia / concurrencia: implementar Cooldowns evaluando `now() - ultimoEvento` > `ACTION_COOLDOWN_MINUTES` en las reglas, y hacer thread-safe la cache de `WeatherService`
