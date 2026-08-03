# Spec: production-map (delta)

## REMOVED Requirements

### Requirement: Selección de macro-zona

**Reason**: A la vista se entra eligiendo una macro-zona en el panel general, así que la
zona ya está decidida al llegar. Un selector adentro invitaba a saltar de zona sin volver,
y dejaba la ruta sin contexto cuando se entraba directo.

**Migration**: Ver el requisito "Acceso exclusivo desde el panel general" en la capacidad
`sensado-macrozona`: se entra desde el panel general y desde adentro sólo se vuelve.

## MODIFIED Requirements

### Requirement: Resumen y sectores a revisar
El Mapa SHALL mostrar el resumen de la zona por estado de salud y una lista de sectores a
revisar ordenada por severidad.

El resumen SHALL contar los cuatro estados por separado —saludables, en observación,
críticos y sin señal— con el mismo color con que cada estado se pinta en la grilla, de modo
que además de resumir haga de leyenda. SHALL ubicarse dentro de la caja de la grilla,
encabezándola.

La lista de sectores a revisar SHALL ser una columna propia y NO SHALL truncarse: si no
entra, scrollea dentro de su tarjeta, porque recortarla en silencio escondería sectores que
requieren atención.

#### Scenario: Resumen de la zona activa
- **WHEN** se muestra una macro-zona
- **THEN** se ven los conteos de saludables, en observación, críticos y sin señal
- **AND** la lista de sectores a revisar prioriza los críticos sobre los de observación

#### Scenario: El resumen explica los colores de la grilla
- **WHEN** el usuario mira un sector coloreado en la grilla
- **THEN** encuentra ese mismo color en el resumen, con el estado que representa

#### Scenario: Más sectores que espacio
- **WHEN** la cantidad de sectores a revisar excede el alto disponible
- **THEN** la lista scrollea dentro de su tarjeta y la página no

### Requirement: Grilla de sectores
El Mapa SHALL mostrar una grilla con los sectores de la zona activa, coloreados por estado
y numerados, clicables hacia el detalle, respetando la disposición configurable de sectores
por fila.

Las celdas SHALL ser cuadradas y la grilla SHALL entrar completa en la pantalla sin scroll,
adaptando el tamaño de celda al espacio disponible. La caja que la contiene SHALL ajustarse
a la grilla, sin franjas vacías a los costados: el ancho que no usa queda para el panel de
valores sensados.

#### Scenario: Abrir sector
- **WHEN** el usuario hace clic en un sector de la grilla
- **THEN** navega al detalle de ese sector

#### Scenario: Celdas cuadradas en cualquier disposición
- **WHEN** se cambia la cantidad de sectores por fila
- **THEN** las celdas siguen siendo cuadradas y la grilla entra completa, ajustando su tamaño

#### Scenario: El ancho sobrante va al sensado
- **WHEN** se renderiza la vista de macro-zona
- **THEN** la caja de la grilla mide lo que mide la grilla
- **AND** el panel de valores sensados ocupa el ancho restante
