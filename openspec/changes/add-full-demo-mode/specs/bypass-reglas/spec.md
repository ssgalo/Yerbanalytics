## ADDED Requirements

### Requirement: Flags de activación y apagado de reglas
En modo `SIMULATED`, el motor de reglas SHALL permitir encender y apagar cualquier regla individualmente en tiempo de ejecución mediante los endpoints `GET /api/demo/rules` y `PUT /api/demo/rules/{ruleName}`. Una regla apagada (`enabled: false`) SHALL ser omitida durante la evaluación sin emitir acciones ni interrumpir la evaluación de otras reglas del DAG.

#### Scenario: Consultar catálogo de reglas con estado de activación
- **GIVEN** el modo demo activo con todas las reglas encendidas por defecto
- **WHEN** se envía una petición `GET /api/demo/rules`
- **THEN** el sistema retorna la lista de todas las reglas con su nombre, etiqueta, rama, prioridad, `enabled: true`, sus cooldowns y sus variables de bypass

#### Scenario: Apagar una regla de bloqueo
- **GIVEN** un sector con bloqueo manual activo (`ManualLockRule` normalmente abortaría con `ABORT_ALL`)
- **AND** la regla `ManualLockRule` fue apagada mediante `PUT /api/demo/rules/ManualLockRule` con `{ "enabled": false }`
- **WHEN** se evalúa la telemetría del sector
- **THEN** `ManualLockRule` es omitida, la cadena de evaluación continúa hacia las siguientes reglas y se permite la actuación de riego si corresponde

### Requirement: Configuración individual de Cooldowns y Variables de Bypass por regla
Cada regla agronómica que implementa restricciones temporales de enfriamiento (*cooldowns* / CDs) o condiciones de bloqueo lógico SHALL soportar una o más flags de cooldown o variables de bypass en modo demo:
- En **modo producción**, cada regla SHALL aplicar estrictamente sus cooldowns y guardas lógicas configuradas por el usuario / agrónomo (ej. 240 minutos en `CicloLecturaRiegoRule`, 720 minutos en `DeficitCriticoRule`, 240 minutos en `PausaTrasAplicacionRule`, ventana horaria 06:00-18:00 en `FueraDeVentanaRiegoRule`).
- En **modo demo**, cada regla SHALL admitir la sobrescritura independiente de uno o más cooldowns o variables de bypass mediante `PUT /api/demo/rules/{ruleName}` (por defecto `cooldownMinutes: 0`), permitiendo omitir tiempos de espera o forzar actuaciones específicas (ej: bypass de riego en curso, bypass de ventana horaria, bypass de dosis máxima).
- El sistema SHALL proveer la operación `PUT /api/demo/rules/cooldowns/zero` para restablecer el cooldown de todas las reglas a 0 simultáneamente.

#### Scenario: Riego sucesivo inmediato con CD = 0 en CicloLecturaRiegoRule
- **GIVEN** un sector que ya fue regado hace 5 minutos en el ciclo de lectura actual
- **AND** la regla `CicloLecturaRiegoRule` configurada con `cooldownMinutes: 0` en modo demo
- **WHEN** se evalúa una telemetría con déficit hídrico para ese sector
- **THEN** `CicloLecturaRiegoRule` no bloquea por ciclo de lectura y permite abrir la electroválvula nuevamente

#### Scenario: Regla con múltiples variables de bypass
- **GIVEN** `CicloLecturaRiegoRule` con `cooldownMinutes: 0` y `bypassRiegoEnCurso: true`
- **WHEN** se evalúa la telemetría mientras el sector tiene una válvula reportada como regando
- **THEN** `CicloLecturaRiegoRule` no bloquea ni por ciclo de lectura ni por riego en curso

#### Scenario: Cooldown individual diferenciado entre reglas
- **GIVEN** `CicloLecturaRiegoRule` configurada con `cooldownMinutes: 0` y `DeficitCriticoRule` configurada con `cooldownMinutes: 60`
- **WHEN** se evalúa la telemetría del sector tras un riego crítico hace 10 minutos
- **THEN** `CicloLecturaRiegoRule` permite el flujo pero `DeficitCriticoRule` bloquea por su cooldown remanente de 50 minutos

#### Scenario: Respeto estricto del cooldown configurado en producción
- **GIVEN** el backend corriendo en modo `REAL`
- **AND** un sector que ya fue regado dentro del ciclo de lectura actual
- **WHEN** se evalúa la telemetría del sector
- **THEN** `CicloLecturaRiegoRule` emite `ABORT_RIEGO` respetando el intervalo productivo de 4 horas

### Requirement: Trazabilidad explícita de reglas apagadas
Cuando una regla es omitida por encontrarse apagada (`enabled = false`), el `RuleOrchestrator` SHALL registrar una entrada en la traza de evaluación (`TrazaRegla`) con el estado `OMITIDA_DESHABILITADA`.

#### Scenario: Inspección de traza con regla apagada
- **GIVEN** la regla `PosponerPorLluviaRule` apagada en modo demo
- **WHEN** se evalúa un pulso de telemetría para el sector
- **THEN** en la traza devuelta por el orquestador (`TrazaEvaluacion`), `PosponerPorLluviaRule` figura con estado `OMITIDA_DESHABILITADA` y motivo explicativo de regla apagada por flag
