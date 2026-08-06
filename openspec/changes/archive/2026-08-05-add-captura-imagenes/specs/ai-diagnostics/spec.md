## MODIFIED Requirements

### Requirement: Modal de imagen
La vista SHALL abrir un modal con la imagen cenital y el detalle del diagnóstico
al hacer clic en una tarjeta, cerrable por botón o por backdrop. Cuando el diagnóstico tenga
una captura asociada, el modal SHALL mostrar **la fotografía real** de esa captura, sin
procesar. Cuando no la tenga, SHALL conservar el respaldo visual actual, de modo que un
diagnóstico sin foto siga siendo consultable.

#### Scenario: Abrir y cerrar modal
- **WHEN** el usuario hace clic en una tarjeta
- **THEN** se abre el modal con sector, macro-zona y confianza
- **AND** al hacer clic en cerrar o en el fondo, el modal se cierra

#### Scenario: Diagnóstico con captura asociada
- **WHEN** el usuario abre un diagnóstico que tiene una captura asociada
- **THEN** el modal muestra la fotografía cenital original sin procesar de esa captura

#### Scenario: Diagnóstico sin captura asociada
- **WHEN** el usuario abre un diagnóstico que no tiene captura asociada
- **THEN** el modal muestra el respaldo visual actual y el resto del detalle, sin espacios
  vacíos ni imágenes rotas

#### Scenario: Imagen que no puede cargarse
- **WHEN** la imagen de la captura no puede recuperarse del backend
- **THEN** el modal recurre al respaldo visual en lugar de mostrar una imagen rota

## ADDED Requirements

### Requirement: Miniatura real en la tarjeta del diagnóstico
La tarjeta de un diagnóstico con captura asociada SHALL mostrar la miniatura de esa captura.
La tarjeta de un diagnóstico sin captura SHALL conservar su respaldo visual actual. La carga
de las miniaturas SHALL ser diferida, para que una grilla larga no dispare la descarga
simultánea de todas las imágenes.

#### Scenario: Tarjeta con captura
- **WHEN** la grilla muestra un diagnóstico que tiene captura asociada
- **THEN** su tarjeta presenta la miniatura de esa captura

#### Scenario: Tarjeta sin captura
- **WHEN** la grilla muestra un diagnóstico sin captura asociada
- **THEN** su tarjeta presenta el respaldo visual actual

#### Scenario: Carga diferida
- **WHEN** la grilla contiene más diagnósticos con imagen de los que entran en pantalla
- **THEN** sólo se descargan las imágenes que se van necesitando al desplazarse
