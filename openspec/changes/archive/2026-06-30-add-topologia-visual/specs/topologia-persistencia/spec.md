# Spec: topologia-persistencia

## ADDED Requirements

### Requirement: Persistencia de la disposición por fila
El backend SHALL persistir la disposición por fila de la topología (cantidad de macro-zonas
por fila y de sectores por fila), independiente de la grilla de macro-zonas y sectores, con
valores por defecto que reproducen la presentación previa (3 macro-zonas por fila y 10 sectores
por fila).

#### Scenario: Disposición por defecto disponible
- **WHEN** se consulta la topología de un vivero que nunca configuró la disposición
- **THEN** el backend devuelve la disposición por defecto (3 macro-zonas por fila y 10 sectores
  por fila)

### Requirement: Actualización de la disposición sin regenerar
El backend SHALL permitir actualizar la disposición por fila mediante
`PUT /api/topologia/disposicion` sin modificar las macro-zonas, los sectores, los dispositivos
ni el historial; y SHALL rechazar (400) valores que no sean enteros positivos dentro de los
límites de la grilla actual (macro-zonas por fila ≤ macro-zonas; sectores por fila ≤ sectores
por macro-zona), sin alterar la disposición guardada.

#### Scenario: Actualización válida
- **WHEN** se invoca `PUT /api/topologia/disposicion` con una disposición dentro de los límites
- **THEN** el backend guarda la nueva disposición
- **AND** no modifica las macro-zonas, sectores, dispositivos ni el historial

#### Scenario: Disposición fuera de rango
- **WHEN** se invoca `PUT /api/topologia/disposicion` con un valor cero, negativo o mayor a la
  cantidad disponible
- **THEN** el backend responde con un error de solicitud (400) y un mensaje que indica la causa
- **AND** no altera la disposición guardada

### Requirement: Disposición en el snapshot del vivero
El backend SHALL incluir la disposición por fila configurada en el snapshot
`GET /api/nursery`, para que el panel general y el mapa de producción puedan renderizar la
grilla con esa disposición.

#### Scenario: Snapshot con disposición
- **WHEN** se invoca `GET /api/nursery`
- **THEN** la respuesta incluye la cantidad de macro-zonas por fila y de sectores por fila
  configurada

## MODIFIED Requirements

### Requirement: Resumen de la topología
El backend SHALL exponer el resumen de la topología actual (cantidad de macro-zonas,
sectores por macro-zona, total de sectores, si hay una topología generada y la disposición por
fila configurada) mediante `GET /api/topologia` (HU-18 CA-01).

#### Scenario: Consulta del resumen
- **WHEN** se invoca `GET /api/topologia`
- **THEN** devuelve la cantidad de macro-zonas, de sectores por macro-zona, el total de
  sectores, si el vivero tiene una topología cargada y la disposición por fila (macro-zonas por
  fila y sectores por fila)
