# Spec: historial-trazabilidad

## ADDED Requirements

### Requirement: Timeline global de acciones
La vista Historial SHALL mostrar un listado cronológico (más reciente primero) de
todas las acciones ejecutadas por el sistema, cada una con su tipo
(Riego / Insumo / Mediasombra), el sector afectado, la marca temporal y el resultado
de ejecución (Efectiva / En seguimiento / Pospuesta / Abortada).

#### Scenario: Render del historial
- **WHEN** el Productor Viverista abre `/historial`
- **THEN** ve el timeline de acciones ordenado del más reciente al más antiguo
- **AND** cada entrada muestra tipo, sector, tiempo y un badge de resultado con su color

#### Scenario: Historial vacío para los filtros
- **WHEN** los filtros aplicados no devuelven ninguna acción
- **THEN** la vista muestra un mensaje de "sin registros" en lugar de un timeline vacío

### Requirement: Cadena de justificación
Cada acción del historial SHALL exponer su cadena de justificación completa
`lectura/diagnóstico IA → decisión del motor → acción física ejecutada`, incluyendo
los parámetros de la acción (duración, volumen o dosis según corresponda).

#### Scenario: Expandir una intervención
- **WHEN** el usuario selecciona una entrada del historial
- **THEN** se despliega la condición desencadenante (lectura o diagnóstico), la
  decisión del motor de reglas y la acción física con sus parámetros

### Requirement: Evolución post-acción
Cuando una acción tiene seguimiento, la vista SHALL mostrar el comparativo
antes/ahora de la métrica afectada, la latencia, el delta y el veredicto
(Efectiva / En seguimiento / Sin efectividad).

#### Scenario: Acción con seguimiento evaluado
- **WHEN** una acción ya fue evaluada tras su latencia
- **THEN** la entrada muestra el valor antes, el valor actual, el delta y el badge
  de veredicto correspondiente

#### Scenario: Acción aún en seguimiento
- **WHEN** la latencia de una acción todavía no venció
- **THEN** la entrada muestra el veredicto "En seguimiento" sin valor final

### Requirement: Filtros del historial
La vista SHALL permitir filtrar las acciones por tipo de acción, macro-zona, sector
y rango de fechas, y mostrar el conteo de resultados.

#### Scenario: Filtrar por tipo y macro-zona
- **WHEN** el usuario elige un tipo de acción y una macro-zona
- **THEN** el timeline muestra solo las acciones que cumplen ambos criterios
- **AND** el contador refleja la cantidad filtrada

#### Scenario: Filtrar por rango de fechas
- **WHEN** el usuario define una fecha desde y hasta
- **THEN** el timeline muestra solo las acciones dentro de ese rango

### Requirement: Registro inalterable
La vista Historial SHALL ser de solo lectura: no SHALL ofrecer ningún control para
editar o eliminar entradas del historial.

#### Scenario: Sin edición desde la interfaz
- **WHEN** el usuario observa cualquier entrada del historial
- **THEN** no encuentra acciones de editar ni eliminar sobre el registro

### Requirement: Origen de datos por entorno
La vista SHALL obtener el historial a través de `DataRepository.getHistory()`, sin
conocer el origen concreto (mock determinístico o backend HTTP).

#### Scenario: Modo backend
- **WHEN** `VITE_DATA_SOURCE=http`
- **THEN** el historial se obtiene de `GET {VITE_API_BASE_URL}/historial`

#### Scenario: Modo mock
- **WHEN** `VITE_DATA_SOURCE` no está definida o vale `mock`
- **THEN** el historial proviene del generador determinístico sembrado
