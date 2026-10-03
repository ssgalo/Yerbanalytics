## ADDED Requirements

### Requirement: Cada comparación de una regla queda registrada
Al evaluar, cada regla SHALL registrar cada comparación que decide su resultado con: etiqueta,
clave del parámetro (si es configurable), valor recibido, operador, valor del umbral usado,
unidad, si es configurable y resultado (`CUMPLE`, `NO_CUMPLE` o `SIN_DATO`). El umbral registrado
SHALL ser el valor usado en esa evaluación, aunque el parámetro cambie después.

#### Scenario: Riego que dispara
- **WHEN** `IrrigationRule` evalúa un sector con humedad de sustrato 38 % y umbral vigente 42 %
- **THEN** la traza tiene la comparación "Humedad de sustrato 38 % < 42 %" con resultado `CUMPLE`

#### Scenario: Sin lectura
- **WHEN** `IrrigationRule` evalúa sin lectura de humedad de sustrato
- **THEN** la comparación queda con recibido vacío y resultado `SIN_DATO`

#### Scenario: Condición no configurable
- **WHEN** `SupplyRule` evalúa el estado del sector contra `critical`
- **THEN** la comparación queda marcada como no configurable y sin clave de parámetro

#### Scenario: Umbral cambiado después
- **WHEN** se guarda un umbral nuevo después de una evaluación
- **THEN** la traza de esa evaluación sigue mostrando el umbral anterior

### Requirement: Estado de cada regla en la evaluación
La traza de una evaluación SHALL incluir todas las reglas registradas, en orden de prioridad, con
su estado: `EVALUADA` (con sus comparaciones y acciones), `OMITIDA_RAMA_BLOQUEADA` (con la regla
que bloqueó la rama) o `NO_ALCANZADA` (después de un bloqueo global).

#### Scenario: Rama bloqueada
- **WHEN** `WeatherOverrideRule` emite `POSTPONE_RIEGO`
- **THEN** `DailyVolumeLimitRule` e `IrrigationRule` figuran como `OMITIDA_RAMA_BLOQUEADA` por
  `WeatherOverrideRule`, y las reglas de las otras ramas como `EVALUADA`

#### Scenario: Bloqueo global
- **WHEN** `ManualLockRule` emite `ABORT_ALL`
- **THEN** todas las reglas siguientes figuran como `NO_ALCANZADA`

### Requirement: Retención en memoria de la última evaluación
El backend SHALL conservar en memoria, por sector, la última traza de cada origen de evaluación
(`TELEMETRIA` y `BARRIDO`). La traza NO SHALL persistirse en la base ni generar filas en
`historial_evento`. Las escrituras concurrentes desde ambos orígenes SHALL ser seguras.

#### Scenario: El barrido no tapa la telemetría
- **WHEN** un sector se evalúa por telemetría y después por barrido
- **THEN** se pueden consultar las dos trazas por separado

#### Scenario: Sin costo en el historial
- **WHEN** el barrido evalúa los 600 sectores
- **THEN** la cantidad de filas nuevas en `historial_evento` es la misma que sin traza

#### Scenario: Reinicio
- **WHEN** el backend se reinicia
- **THEN** no hay trazas hasta la próxima evaluación de cada sector

### Requirement: Consulta de la última evaluación
El backend SHALL exponer `GET /api/rules/evaluaciones/{sectorId}` con un parámetro opcional
`origen`. Sin `origen` SHALL devolver la traza más reciente de las dos.

#### Scenario: Traza disponible
- **WHEN** se consulta un sector evaluado
- **THEN** responde 200 con sector, zona, origen, timestamp y la traza de cada regla

#### Scenario: Sector sin evaluar
- **WHEN** se consulta un sector existente que no se evaluó desde el arranque
- **THEN** responde 204

#### Scenario: Sector inexistente
- **WHEN** se consulta un sector que no existe
- **THEN** responde 404
