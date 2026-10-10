# configuracion-agronomica

## Purpose

Configuracion de umbrales, limites operativos y plan de rustificacion (HU-15).

## Requirements

### Requirement: Vista de configuración por bloques
La vista Configuración SHALL presentar la configuración agronómica agrupada en
bloques editables: umbrales de métricas, límites de riego, límites de insumos, plan
de mediasombra/rustificación y parámetros de seguimiento post-acción.

#### Scenario: Render de la configuración vigente
- **WHEN** el Ingeniero Agrónomo abre `/configuracion`
- **THEN** ve los valores vigentes de cada bloque cargados en formularios editables

### Requirement: Edición de umbrales de métricas
La vista SHALL permitir editar, por cada una de las 5 métricas, sus bandas `ideal`,
`warn` y `crit`.

#### Scenario: Editar una banda
- **WHEN** el usuario modifica un valor de banda de una métrica
- **THEN** el formulario refleja el nuevo valor y lo incluye al guardar

### Requirement: Edición de límites operativos
La vista SHALL permitir definir el tiempo máximo de apertura y el volumen diario
máximo de riego, la dosis máxima de insumo por 24 h, la apertura máxima de la
mediasombra y el plan de rustificación por etapas (día desde, día hasta, % apertura).

#### Scenario: Editar un límite de actuador
- **WHEN** el usuario modifica un límite operativo (p. ej. volumen diario de riego)
- **THEN** el formulario lo incluye al guardar

#### Scenario: Editar el plan de rustificación
- **WHEN** el usuario ajusta las etapas del plan de rustificación
- **THEN** la vista muestra el cronograma actualizado de días y porcentaje de apertura

### Requirement: Edición de parámetros de seguimiento
La vista SHALL permitir definir la latencia de espera y el delta mínimo de mejora que
catalogan una acción como efectiva.

#### Scenario: Editar latencia y delta
- **WHEN** el usuario modifica la latencia o el delta de seguimiento
- **THEN** el formulario los incluye al guardar

### Requirement: Validación en cliente
La vista SHALL validar los valores antes de guardar (bandas coherentes y dentro del
rango fisiológico, límites positivos, etapas sin solapamiento), marcando el campo
inválido y bloqueando el guardado mientras haya errores.

#### Scenario: Valor inválido bloquea el guardado
- **WHEN** el usuario ingresa un valor fuera del rango permitido o incoherente
- **THEN** la vista marca el campo como inválido y deshabilita el botón Guardar

### Requirement: Guardado y restablecimiento
La vista SHALL guardar la configuración vía `DataRepository.saveConfig()` mostrando
feedback de éxito o error, y SHALL ofrecer restablecer los valores de fábrica.

#### Scenario: Guardado exitoso
- **WHEN** el usuario guarda una configuración válida
- **THEN** la vista confirma el guardado con un mensaje de éxito

#### Scenario: Restablecer valores de fábrica
- **WHEN** el usuario elige restablecer
- **THEN** los formularios vuelven a los valores de fábrica

### Requirement: Origen de datos por entorno
La vista SHALL obtener y guardar la configuración a través de `DataRepository`, sin
conocer el origen concreto (mock o backend HTTP).

#### Scenario: Modo backend
- **WHEN** `VITE_DATA_SOURCE=http`
- **THEN** la configuración se obtiene de `GET {VITE_API_BASE_URL}/configuracion` y se
  guarda con `PUT {VITE_API_BASE_URL}/configuracion`

#### Scenario: Modo mock
- **WHEN** `VITE_DATA_SOURCE` no está definida o vale `mock`
- **THEN** la configuración proviene del mock determinístico y el guardado actualiza el
  cache en memoria

### Requirement: Sensing pull interval editor

The configuration view SHALL let the user edit the sensing pull interval (seconds) in the
intervals block, labelled so it cannot be confused with the irrigation read cycle
(`intervaloSensadoMinutos`). Next to the field it SHALL show a hint with the effective stale
threshold that the edited value would produce (`max(floor, 3 × X)`). The value SHALL be saved
through `DataRepository.saveConfig()` together with the rest of the configuration, in both mock and
http modes.

#### Scenario: Current value loaded
- **GIVEN** the saved interval is 60 s
- **WHEN** the user opens `/configuracion`
- **THEN** the field shows 60 and the hint shows a 180 s threshold (floor 90 s)

#### Scenario: Hint reflects the backend-computed threshold after saving
- **GIVEN** floor = 90 s and a stored interval of 60 s (hint shows 180 s)
- **WHEN** the user types 120
- **THEN** the hint keeps showing 180 s, labelled as recomputed on save
- **WHEN** the user saves
- **THEN** the hint shows the `effectiveStaleThresholdSeconds` returned by the backend (360 s); the browser never computes the formula

#### Scenario: Out-of-range value blocks saving
- **WHEN** the user enters 5 or 4000
- **THEN** the field is marked invalid and Save is disabled

#### Scenario: Saved value sent
- **WHEN** the user saves a valid interval of 30
- **THEN** `saveConfig()` receives `sensingPullIntervalSeconds: 30`

#### Scenario: Factory reset
- **WHEN** the user restores factory values
- **THEN** the field returns to 60
