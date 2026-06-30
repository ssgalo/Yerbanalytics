# Spec: dashboard

## MODIFIED Requirements

### Requirement: Vista general del vivero
El Panel general SHALL mostrar las macro-zonas de la topología actual, cada una con un
mini-heatmap de sus sectores coloreados por estado. La cantidad de macro-zonas por fila y de
sectores por fila SHALL respetar la disposición configurada en la topología, en vez de una
distribución fija.

#### Scenario: Disposición configurada
- **WHEN** se renderiza el panel general
- **THEN** las macro-zonas se distribuyen según la cantidad de macro-zonas por fila configurada
- **AND** cada mini-heatmap distribuye sus sectores según la cantidad de sectores por fila
  configurada

#### Scenario: Abrir una zona
- **WHEN** el usuario hace clic en una macro-zona
- **THEN** navega al Mapa de producción con esa zona seleccionada

#### Scenario: Abrir un sector desde una celda
- **WHEN** el usuario hace clic en una celda del heatmap
- **THEN** navega al detalle de ese sector
