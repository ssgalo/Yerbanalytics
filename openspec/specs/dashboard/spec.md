# dashboard

## Purpose

Panel general: KPIs del vivero, atencion prioritaria y actividad del sistema.

## Requirements

### Requirement: Fila de KPIs
El Panel general SHALL mostrar 4 KPIs: sectores saludables (con % y barra),
sectores en alerta (observación + crítico), hardware fuera de servicio, y
acciones autónomas del día (riego/insumo/sombra).

#### Scenario: KPI de saludables
- **WHEN** se renderiza el panel
- **THEN** muestra `sano / total` y el porcentaje con su barra de progreso

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

### Requirement: Paneles laterales y de actividad
El Panel general SHALL mostrar: atención prioritaria (sectores críticos/alerta),
clima y riesgo (temp, UV, pronóstico), feed de actividad del sistema, y
diagnósticos recientes.

#### Scenario: Pulso en críticos
- **WHEN** un sector prioritario es crítico
- **THEN** su indicador usa la animación de pulso (`ybPulse`)
