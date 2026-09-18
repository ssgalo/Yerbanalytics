# motor-reglas

> **Estado de implementación:** ✅ Implementado completamente en `release/Sensado-DiagnosticoVisual-RiegoAutomatico` (R1) y `release/AutomatizacionAgronomicaConfigurable-y-AlertasInteligentes` (R2).
> Clases: `engine/Rule`, `RuleContext`, `RuleOrchestrator`, `ActionExecutor`, `rules/*`, `weather/WeatherService`, `NurseryWatchdog`.

## Purpose

Motor de reglas agronómicas del backend: pipeline de decisiones autonómas que
evalúa telemetría MQTT, pronóstico climático, diagnóstico IA y bloqueos manuales
para emitir acciones físicas sobre actuadores de cada sector del vivero.
Cubre HU-06, HU-07, HU-08, HU-09, HU-10 y HU-19.

## Requirements

### Requirement: Pipeline de reglas con prioridad y corte anticipado

El backend SHALL evaluar las reglas de negocio en un pipeline ordenado por prioridad
numérica creciente. Si una regla emite una acción bloqueante (`ABORT_ALL`,
`ABORT_RIEGO`, `ABORT_INSUMO`, `POSTPONE_RIEGO`), el orquestador SHALL detener la
evaluación de las reglas de menor prioridad afectadas. El orden SHALL ser explícito
y determinístico, no dependiente del orden de inyección de Spring.

#### Scenario: Bloqueo manual corta toda la cadena

- **WHEN** existe un bloqueo manual activo para el sector evaluado o su zona
- **THEN** `BloqueoManualRule` (prioridad 0) emite `ABORT_ALL` y ninguna regla
  posterior se ejecuta para ese sector en ese ciclo

#### Scenario: Lluvia inminente pospone el riego sin abortar insumo

- **WHEN** `ClimaOverrideRule` detecta probabilidad de lluvia ≥ umbral configurado
- **THEN** emite `POSTPONE_RIEGO` y las reglas de insumo y mediasombra continúan evaluándose

#### Scenario: Reglas se evalúan en orden de prioridad

- **WHEN** se ejecuta un ciclo de evaluación con múltiples reglas activas
- **THEN** las reglas se evalúan de menor a mayor prioridad numérica (0 → 20)

### Requirement: Contexto inmutable de evaluación

El motor SHALL construir un `RuleContext` inmutable una sola vez por ciclo de
evaluación, consolidando: sector actual, zona, métricas evaluadas, configuración
agronómica vigente, pronóstico climático (o `null` si la API falló), resultado
del diagnóstico IA (o `null` si no corrió), flag de bloqueo manual activo y
timestamp del momento de evaluación. Ninguna regla SHALL modificar el contexto.

#### Scenario: Fallo de API climática no detiene la evaluación

- **WHEN** el `WeatherService` no puede obtener el pronóstico (API caída)
- **THEN** `RuleContext.forecast()` vale `null` y el motor evalúa todas las demás
  reglas sin bloquearse

#### Scenario: Diagnóstico IA ausente no bloquea el riego

- **WHEN** el sector no tiene diagnóstico IA reciente
- **THEN** `RuleContext.diagnosis()` vale `null`, `InsumoRule` emite `NOOP_INFO`
  y `RiegoRule` evalúa igual la humedad

### Requirement: Materialización de acciones con efectos reales

El `ActionExecutor` SHALL ser el único punto donde las acciones emitidas por el
motor se convierten en efectos físicos: publicación MQTT de comandos al actuador,
persistencia en `historial_evento` y actualización del campo de estado del sector.
El executor SHALL registrar un `NOOP_INFO` (Registro de Inacción) cuando el motor
decide no actuar, de modo que el usuario pueda consultar el motivo en el historial.

#### Scenario: Comando MQTT al activar la válvula

- **WHEN** `RiegoRule` emite `ACTIVAR_VALVULA` y el sector transiciona desde "no regando"
- **THEN** el `ActionExecutor` publica el comando `{"actuador":"valve","accion":"ON"}`
  al tópico `nursery/zone/{zonaId}/sector/{sectorId}/command` y registra el riego
  en el historial

#### Scenario: Registro de inacción al posponer riego

- **WHEN** `ClimaOverrideRule` emite `POSTPONE_RIEGO`
- **THEN** el `ActionExecutor` persiste un evento de inacción en el historial con
  el motivo de postergación (lluvia inminente), sin publicar comando al actuador

#### Scenario: No se duplica el historial en el mismo estado

- **WHEN** la válvula ya estaba en estado "Regando" y sigue en "Regando"
- **THEN** el `ActionExecutor` no genera un nuevo evento en `historial_evento`

### Requirement: Trigger reactivo y Watchdog proactivo

El motor SHALL evaluarse en dos caminos complementarios:

- **Reactivo**: disparado por cada paquete de telemetría MQTT entrante.
- **Proactivo (Watchdog)**: un scheduler periódico que itera todos los sectores
  para detectar nodos caídos (sin telemetría reciente) mediante `StaleSensorRule`
  y para evaluar reglas independientes de telemetría fresca (como el plan de días
  de `MediasombraRule`).

#### Scenario: Watchdog detecta nodo sin reportar

- **WHEN** un sector supera el umbral de silencio (`stale-threshold-ms`) sin recibir
  telemetría MQTT
- **THEN** el Watchdog dispara la evaluación de ese sector y `StaleSensorRule` emite
  una alerta de nodo desconectado

#### Scenario: Cambio de etapa de rustificación sin telemetría

- **WHEN** el día del ciclo de siembra entra en una nueva etapa del plan de rustificación
  y no ha llegado telemetría reciente
- **THEN** el Watchdog dispara la evaluación y `MediasombraRule` emite `MOVER_MEDIASOMBRA`

### Requirement: Idempotencia mediante cooldowns

El motor SHALL aplicar un periodo de enfriamiento (`ACTION_COOLDOWN_MINUTES`) antes
de re-emitir el mismo comando a un sector, para proteger al hardware de inundaciones
de comandos si los sensores envían telemetría muy seguido. El valor SHALL ser
configurable via variable de entorno.

#### Scenario: Riego bloqueado por cooldown activo

- **WHEN** el sector ya recibió un comando de riego y no venció el cooldown
- **THEN** `RiegoRule` emite `NOOP_INFO` indicando el tiempo restante, sin activar
  la válvula nuevamente

### Requirement: Separación estricta de tópicos MQTT

El motor SHALL usar tres tópicos distintos por sector para separar las
responsabilidades del protocolo embebido ↔ backend:

- **Telemetría** (`telemetry`): el ESP32 publica, el backend suscribe.
- **Comando** (`command`): el backend publica (QoS 1 — `MqttPahoMessageHandler.setDefaultQos(1)`), el ESP32 suscribe. El nodo es idempotente por diseño ante duplicados.
- **Confirmación** (`ack`): el ESP32 publica al finalizar la ejecución del comando.

No SHALL existir eco entre tópicos (el backend NO suscribe su propio tópico de comando).

#### Scenario: Tópico de comando correcto

- **WHEN** el motor publica un comando para el sector "S-1" de la zona "Z-1"
- **THEN** el tópico de destino es exactamente `nursery/zone/Z-1/sector/S-1/command`

### Requirement: Mapeo de reglas a releases

Las reglas SHALL incorporarse al motor siguiendo el orden de releases del producto:

| Prioridad | Regla | Release | Historias |
|-----------|-------|---------|-----------|
| 0 | `BloqueoManualRule` | R2 | HU-19 |
| 1 | `StaleSensorRule` | R1 | HU-02 |
| 2 | `ClimaOverrideRule` | R2 | HU-09 |
| 5 | `DosisLimiteRule` | R2 | HU-07 |
| 10 | `RiegoRule` | R1 | HU-06 |
| 11 | `InsumoRule` | R2 | HU-07 |
| 12 | `MediasombraRule` | R2 | HU-08 |
| 20 | `SeguimientoRule` | R4 | HU-12 |

#### Scenario: Scaffold del motor no cambia comportamiento observable

- **WHEN** se extrae la lógica inline de `NurseryService.updateTelemetry()` a las
  reglas correspondientes
- **THEN** el resultado de riego e insumo es el mismo que antes del refactor
