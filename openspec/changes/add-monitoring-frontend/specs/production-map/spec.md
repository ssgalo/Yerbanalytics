# Spec: production-map

## ADDED Requirements

### Requirement: Selección de macro-zona
El Mapa de producción SHALL mostrar pestañas para las 6 macro-zonas, con la
cantidad en alerta de cada una, y permitir cambiar la zona activa.

#### Scenario: Cambio de zona
- **WHEN** el usuario selecciona una pestaña de zona
- **THEN** la grilla y el resumen reflejan esa zona

### Requirement: Grilla de sectores
El Mapa SHALL mostrar una grilla 10×10 de los 100 sectores de la zona activa,
coloreados por estado y numerados, clicables hacia el detalle.

#### Scenario: Abrir sector
- **WHEN** el usuario hace clic en un sector de la grilla
- **THEN** navega al detalle de ese sector

### Requirement: Resumen y sectores a revisar
El Mapa SHALL mostrar el resumen de la zona (saludables / alerta / sin señal) y
una lista de sectores a revisar ordenada por severidad.

#### Scenario: Resumen de la zona activa
- **WHEN** se selecciona una zona
- **THEN** se muestran los conteos de saludables, en alerta y sin señal
- **AND** la lista de sectores a revisar prioriza los críticos sobre los de observación
