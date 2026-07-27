# ai-diagnostics

## Purpose

Presentacion de los diagnosticos del modelo de vision sobre los plantines.

## Requirements

### Requirement: Listado de diagnósticos con filtros
La vista de Diagnósticos de IA SHALL mostrar una grilla de tarjetas (estado,
sector, zona, confianza, severidad, hora) filtrable por estado/anomalía y por
severidad.

#### Scenario: Filtro por estado
- **WHEN** el usuario elige un estado (p. ej. "Clorosis")
- **THEN** la grilla muestra solo los diagnósticos de ese estado

#### Scenario: Filtro por severidad
- **WHEN** el usuario elige una severidad
- **THEN** la grilla se filtra y el contador se actualiza

### Requirement: Modal de imagen
La vista SHALL abrir un modal con la imagen cenital y el detalle del diagnóstico
al hacer clic en una tarjeta, cerrable por botón o por backdrop.

#### Scenario: Abrir y cerrar modal
- **WHEN** el usuario hace clic en una tarjeta
- **THEN** se abre el modal con sector, macro-zona y confianza
- **AND** al hacer clic en cerrar o en el fondo, el modal se cierra
