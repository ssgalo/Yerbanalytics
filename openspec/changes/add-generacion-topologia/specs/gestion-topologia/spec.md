# Spec: gestion-topologia

## ADDED Requirements

### Requirement: Panel de generación de topología
El frontend SHALL ofrecer al Administrador una vista para definir la estructura física del
vivero indicando la cantidad de macro-zonas y de sectores por macro-zona, y disparar la
generación de la grilla lógica (HU-18 CA-01).

#### Scenario: Generar la grilla desde el formulario
- **WHEN** el Administrador ingresa la cantidad de macro-zonas y de sectores por macro-zona
  y confirma
- **THEN** el frontend solicita la generación de la topología
- **AND** muestra la confirmación con la cantidad de macro-zonas y sectores resultante

### Requirement: Resumen de topología actual
El frontend SHALL mostrar el resumen de la topología cargada (cantidad de macro-zonas,
sectores por macro-zona y total de sectores), o indicar que el vivero no tiene topología
cargada (HU-18 CA-01).

#### Scenario: Vista del resumen
- **WHEN** el Administrador abre la vista de topología
- **THEN** el frontend muestra la cantidad de macro-zonas, de sectores por macro-zona y el
  total de sectores de la topología actual

### Requirement: Confirmación de regeneración
El frontend SHALL exigir una confirmación explícita antes de regenerar una topología ya
cargada, advirtiendo que se reemplaza la grilla y se descartan los dispositivos y el
historial asociados (HU-18 CA-01).

#### Scenario: Regenerar sobre una topología existente
- **WHEN** el Administrador intenta generar una nueva topología sobre un vivero que ya
  tiene una grilla cargada
- **THEN** el frontend pide una confirmación explícita antes de enviar la regeneración

### Requirement: Origen de datos por entorno
El frontend SHALL consumir la topología a través de la misma interfaz de repositorio que el
resto de la app, resolviendo contra el mock determinístico o el backend real según el
entorno, sin cambios en los componentes (HU-18 CA-01).

#### Scenario: Mock y backend intercambiables
- **WHEN** la app corre con el origen de datos mock o con el backend real
- **THEN** la vista de topología obtiene y genera la grilla mediante la misma interfaz de
  repositorio, sin tocar los componentes
