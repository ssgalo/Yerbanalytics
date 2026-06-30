# Spec: production-map

## MODIFIED Requirements

### Requirement: Grilla de sectores
El Mapa SHALL mostrar una grilla con los sectores de la zona activa, coloreados por estado y
numerados, clicables hacia el detalle. La cantidad de sectores por fila SHALL respetar la
disposición configurada en la topología, en vez de una distribución fija.

#### Scenario: Disposición configurada
- **WHEN** se renderiza la grilla de una zona
- **THEN** los sectores se distribuyen según la cantidad de sectores por fila configurada

#### Scenario: Abrir sector
- **WHEN** el usuario hace clic en un sector de la grilla
- **THEN** navega al detalle de ese sector
