## ADDED Requirements

### Requirement: Sección "Motor de reglas"
El dashboard SHALL ofrecer una sección "Motor de reglas" en la navegación lateral, con dos vistas:
Parámetros e Inspector. Todos sus datos SHALL obtenerse a través de `DataRepository`, y SHALL
funcionar en los modos `mock` y `http`.

#### Scenario: Modo demo
- **WHEN** el dashboard corre con `VITE_DATA_SOURCE=mock`
- **THEN** la sección muestra el catálogo de las reglas y una traza de ejemplo, sin llamadas de red

#### Scenario: Esquema del DAG por repositorio
- **WHEN** cualquier vista necesita el esquema del DAG
- **THEN** lo obtiene de `DataRepository` y no con una llamada de red directa

### Requirement: Parámetros vistos por regla
La vista Parámetros SHALL listar las reglas agrupadas por rama y en orden de prioridad, y bajo cada
regla los parámetros que declara, con etiqueta, valor vigente, unidad, rango, referencia a la
especificación y si está modificado respecto de fábrica. Una regla sin parámetros SHALL indicarlo.

#### Scenario: Regla con parámetros
- **WHEN** el usuario abre la regla de riego
- **THEN** ve cada uno de sus parámetros con su valor vigente y su unidad

#### Scenario: Regla sin parámetros
- **WHEN** el usuario abre `ManualLockRule`
- **THEN** ve "Sin parámetros configurables"

### Requirement: Un parámetro compartido es un solo valor
Un parámetro usado por varias reglas SHALL aparecer bajo cada una, identificado como compartido y
con la lista de las otras reglas. Editarlo en cualquiera de sus apariciones SHALL reflejarse de
inmediato en todas, y SHALL guardarse una sola vez.

#### Scenario: Edición desde una regla
- **WHEN** el usuario cambia un parámetro compartido bajo la regla A
- **THEN** el valor nuevo se ve también bajo la regla B, y al guardar se envía un único cambio para
  esa clave

#### Scenario: Vista por parámetro
- **WHEN** el usuario activa "Ver por parámetro"
- **THEN** cada parámetro aparece una sola vez con las reglas que lo usan

### Requirement: Edición validada
La vista SHALL ofrecer un campo acorde al tipo (número, hora, ventana horaria), validar en cliente
tipo, rango y decimales a partir de los datos del catálogo, deshabilitar el guardado mientras haya
errores, permitir restablecer un parámetro a fábrica y mostrar junto a cada parámetro los errores
que devuelva el servidor.

#### Scenario: Fuera de rango en cliente
- **WHEN** el usuario escribe un valor fuera del rango del parámetro
- **THEN** el campo se marca inválido y "Guardar" queda deshabilitado

#### Scenario: Error del servidor
- **WHEN** el servidor rechaza el guardado por una restricción cruzada
- **THEN** el mensaje se muestra junto a los parámetros involucrados y el borrador se conserva

### Requirement: Escala a decenas de reglas
La vista SHALL permitir buscar por regla o parámetro, filtrar por rama y mostrar sólo los
modificados. Las reglas SHALL aparecer colapsadas con un resumen de cantidad de parámetros y
modificados.

#### Scenario: Búsqueda
- **WHEN** el usuario busca "lluvia"
- **THEN** se ven sólo las reglas cuyo nombre o alguno de cuyos parámetros coincide

### Requirement: Inspector con recibido vs. umbral
El Inspector SHALL permitir elegir un sector y un origen de evaluación, y SHALL mostrar el DAG del
motor coloreado según la traza: regla evaluada que pasó, que bloqueó, que pospuso, que accionó,
omitida por rama bloqueada y no alcanzada. Cada nodo de regla evaluada SHALL mostrar sus
comparaciones como "recibido operador umbral" con su resultado. Al seleccionar un nodo SHALL
mostrar todas sus comparaciones, sus acciones con el motivo y un acceso a editar sus parámetros.

#### Scenario: Riego que no dispara
- **WHEN** la traza tiene humedad 55 % contra umbral 42 %
- **THEN** el nodo de riego muestra "55 % < 42 %" marcado como no cumplido y el nodo terminal de la
  rama indica que no se regó

#### Scenario: Rama pospuesta por lluvia
- **WHEN** la traza tiene `WeatherOverrideRule` con `POSTPONE_RIEGO`
- **THEN** ese nodo se ve como "pospuso" con la probabilidad recibida contra el umbral, y las reglas
  siguientes de la rama como omitidas

#### Scenario: Sin traza
- **WHEN** el sector todavía no se evaluó desde el arranque del backend
- **THEN** el Inspector lo indica y no pinta el DAG con datos inventados

#### Scenario: Llegada desde el sector
- **WHEN** el usuario entra al Inspector desde el detalle de un sector
- **THEN** el sector ya viene seleccionado
