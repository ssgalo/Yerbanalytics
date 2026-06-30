# Spec: topologia-persistencia

## ADDED Requirements

### Requirement: Generación de la grilla lógica
El backend SHALL generar la grilla lógica del vivero a partir de la cantidad de
macro-zonas y de sectores por macro-zona indicada por el Administrador, creando cada
macro-zona y cada sector en estado offline (con los mismos defaults que la grilla
sembrada) y dejándolos disponibles para el mapa de producción (HU-18 CA-01).

#### Scenario: Generación sobre un vivero sin topología
- **WHEN** se invoca `POST /api/topologia` con `macroZonas` y `sectoresPorMacroZona` sobre
  un vivero sin topología cargada
- **THEN** el backend crea las macro-zonas y los sectores indicados en estado offline
- **AND** quedan disponibles en `GET /api/nursery` para el mapa de producción

### Requirement: Identificadores únicos
El backend SHALL asignar a cada macro-zona y a cada sector un identificador único e
irrepetible siguiendo la convención `MZ-{z}` para las macro-zonas y `MZ-{z}-{NNN}` para
los sectores, con el número de sector completado a tres dígitos (HU-18 CA-01).

#### Scenario: Ids generados sin colisiones
- **WHEN** se genera una topología de N macro-zonas × M sectores
- **THEN** existen exactamente N macro-zonas (`MZ-1`..`MZ-N`) y N×M sectores
  (`MZ-{z}-001`..`MZ-{z}-{M}`)
- **AND** ningún identificador de macro-zona o de sector se repite

### Requirement: Rangos válidos de la topología
El backend SHALL rechazar una generación cuya cantidad de macro-zonas o de sectores por
macro-zona no sea un entero positivo dentro de los límites operativos, sin modificar la
topología existente (HU-18 CA-01).

#### Scenario: Cantidad fuera de rango
- **WHEN** se invoca `POST /api/topologia` con `macroZonas` o `sectoresPorMacroZona` igual
  a cero, negativo o por encima del límite operativo
- **THEN** el backend responde con un error de solicitud (400) y un mensaje que indica la
  causa, sin alterar la topología

### Requirement: Regeneración guardada
El backend SHALL generar la topología sólo cuando el vivero está vacío y SHALL rechazar la
operación cuando ya existe una topología cargada, salvo que el request pida regenerar
explícitamente; al regenerar SHALL reemplazar la grilla y limpiar las referencias
colgantes (dispositivos e historial) para no dejar punteros a sectores inexistentes
(HU-18 CA-01).

#### Scenario: Topología ya cargada sin pedir regenerar
- **WHEN** se invoca `POST /api/topologia` sobre un vivero que ya tiene topología y sin el
  flag de regeneración
- **THEN** el backend responde con un conflicto (409) y no modifica la grilla existente

#### Scenario: Regeneración explícita
- **WHEN** se invoca `POST /api/topologia` con el flag de regeneración sobre un vivero con
  topología
- **THEN** el backend borra las macro-zonas, sectores, dispositivos e historial existentes
  y crea la nueva grilla con los identificadores de la topología solicitada

### Requirement: Resumen de la topología
El backend SHALL exponer el resumen de la topología actual (cantidad de macro-zonas,
sectores por macro-zona, total de sectores y si hay una topología generada) mediante
`GET /api/topologia` (HU-18 CA-01).

#### Scenario: Consulta del resumen
- **WHEN** se invoca `GET /api/topologia`
- **THEN** devuelve la cantidad de macro-zonas, de sectores por macro-zona, el total de
  sectores y si el vivero tiene una topología cargada
