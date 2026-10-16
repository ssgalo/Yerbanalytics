## MODIFIED Requirements

### Requirement: Listado de diagnósticos con filtros
La vista de Diagnósticos de IA SHALL mostrar una grilla de tarjetas (estado, sector, zona, nivel de
confianza, severidad, hora) filtrable por estado/anomalía y por severidad. La severidad SHALL
mostrarse sólo en estados anómalos.

#### Scenario: Filtro por estado
- **WHEN** el usuario elige un estado (p. ej. "Clorosis")
- **THEN** la grilla muestra solo los diagnósticos de ese estado

#### Scenario: Filtro por severidad
- **WHEN** el usuario elige una severidad
- **THEN** la grilla se filtra y el contador se actualiza

#### Scenario: Tarjeta de un diagnóstico Sano
- **WHEN** la grilla muestra un diagnóstico Sano
- **THEN** la tarjeta muestra su nivel de confianza y no muestra severidad

### Requirement: Modal de imagen
La vista SHALL abrir un modal con la imagen cenital y el detalle del diagnóstico
al hacer clic en una tarjeta, cerrable por botón o por backdrop. Cuando el diagnóstico tenga
una captura asociada, el modal SHALL mostrar **la fotografía real** de esa captura, sin
procesar. Cuando no la tenga, SHALL conservar el respaldo visual actual, de modo que un
diagnóstico sin foto siga siendo consultable. Cuando el diagnóstico tenga detecciones, el modal
SHALL ofrecer un selector *Original · Diagnóstico · Todas* que arranca en *Original*: *Original* muestra
la fotografía sin cajas; *Diagnóstico* superpone sólo las cajas del estado diagnosticado; *Todas*
superpone las cajas de todas las clases, cada una con su color, con las del estado diagnosticado
resaltadas con un trazo distinto, y una leyenda que nombra cada clase y su trazo. El dashboard NO SHALL
mostrar la cantidad de detecciones ni el score de las cajas. Las cajas SHALL pedirse al
backend recién cuando se elija una vista que las muestre.

#### Scenario: Abrir y cerrar modal
- **WHEN** el usuario hace clic en una tarjeta
- **THEN** se abre el modal con sector, macro-zona y nivel de confianza
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

#### Scenario: Ver las cajas del diagnóstico
- **WHEN** el usuario elige *Diagnóstico* en un diagnóstico de Clorosis con detecciones
- **THEN** la fotografía muestra superpuestas sólo las cajas de Clorosis, alineadas con las hojas

#### Scenario: Ver todas las cajas
- **WHEN** el usuario elige *Todas*
- **THEN** la fotografía muestra las cajas de todas las clases con su color, las del estado
  diagnosticado con trazo resaltado, y una leyenda con el nombre de cada clase, sin cantidades

#### Scenario: Volver a la imagen original
- **WHEN** el usuario elige *Original*
- **THEN** la fotografía se muestra sin cajas

#### Scenario: Diagnóstico sin detecciones
- **WHEN** el usuario abre un diagnóstico sin detecciones (carga manual o derivado del sector)
- **THEN** el modal no ofrece el selector de cajas

#### Scenario: Cajas que no pueden cargarse
- **WHEN** el pedido de las detecciones falla
- **THEN** el modal sigue mostrando la fotografía original y avisa que no se pudieron cargar las cajas
