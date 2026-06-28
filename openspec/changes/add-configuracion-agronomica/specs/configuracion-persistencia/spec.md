# Spec: configuracion-persistencia

## ADDED Requirements

### Requirement: Valores de fábrica seguros
El backend SHALL inicializar la configuración agronómica con valores de fábrica
seguros para *Ilex paraguariensis* (bandas de las 5 métricas, límites de actuadores,
plan de rustificación y parámetros de seguimiento), de modo que el sistema opere sin
requerir calibración inicial.

#### Scenario: Configuración disponible al primer arranque
- **WHEN** se consulta `GET /api/configuracion` en un entorno recién sembrado
- **THEN** devuelve los valores de fábrica para umbrales, límites, plan de
  rustificación y parámetros de seguimiento

### Requirement: Persistencia de la configuración
El backend SHALL persistir la configuración agronómica editable: las bandas
(`ideal`, `warn`, `crit`) de cada métrica, los límites operativos de actuadores
(tiempo y volumen de riego, dosis máxima de insumo, apertura máxima de mediasombra),
las etapas del plan de rustificación y los parámetros de seguimiento (latencia y
delta de recuperación).

#### Scenario: Guardado de un parámetro válido
- **WHEN** se invoca `PUT /api/configuracion` con un cuerpo válido
- **THEN** el backend actualiza la base y la nueva configuración queda reflejada en el
  siguiente `GET /api/configuracion`

### Requirement: Validación fisiológica al guardar
El backend SHALL rechazar configuraciones inválidas: bandas incoherentes o fuera del
rango fisiológico de fábrica, límites de actuadores no positivos, apertura fuera de
`[0, 100]`, o etapas de rustificación solapadas o con días invertidos.

#### Scenario: Valor fuera del rango fisiológico
- **WHEN** se envía una banda fuera del rango fisiológico seguro o con orden
  incoherente (p. ej. `idealMin > idealMax`)
- **THEN** el backend rechaza el cambio con un error 400 y un mensaje que indica el
  parámetro inválido, sin persistir nada

#### Scenario: Límite de actuador no positivo
- **WHEN** se envía un tiempo, volumen, dosis o porcentaje fuera de rango
- **THEN** el backend rechaza el cambio con un error 400 sin persistir

### Requirement: Auditoría del cambio de configuración
El backend SHALL registrar, en cada guardado válido, el usuario que realizó el cambio
y la marca temporal, y SHALL asentar el cambio en el historial inmutable.

#### Scenario: Guardado audita autor y timestamp
- **WHEN** un guardado válido se persiste
- **THEN** la configuración registra `updatedBy` y `updatedTs`
- **AND** queda un evento en el historial que refleja la intervención de configuración

### Requirement: Endpoints de configuración
El backend SHALL exponer `GET /api/configuracion` (configuración vigente completa) y
`PUT /api/configuracion` (actualización validada). No SHALL exponer otros verbos de
escritura sobre la configuración.

#### Scenario: Consulta de la configuración vigente
- **WHEN** se invoca `GET /api/configuracion`
- **THEN** devuelve el agregado con umbrales, límites, plan de rustificación y
  parámetros de seguimiento

### Requirement: Umbrales activos en el motor
El backend SHALL usar los umbrales persistidos para calcular el estado de las métricas
de cada sector y para derivar el disparo del riego, en lugar de constantes de código.

#### Scenario: Cambiar una banda altera el estado de los sectores
- **WHEN** se guarda una nueva banda `ideal`/`warn` para una métrica
- **THEN** el cálculo de estado de los sectores en `GET /api/nursery` refleja la nueva
  banda en las lecturas siguientes

### Requirement: Parámetros de seguimiento activos
El backend SHALL usar la latencia y el delta de recuperación persistidos para evaluar
la efectividad de las acciones post-acción.

#### Scenario: Seguimiento usa la latencia configurada
- **WHEN** se registra una acción con seguimiento
- **THEN** la evaluación de efectividad emplea la latencia y el delta vigentes en la
  configuración persistida
