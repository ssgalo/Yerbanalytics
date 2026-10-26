## ADDED Requirements

### Requirement: Recorte opcional en el alta del diagnóstico
`POST /api/diagnosticos` SHALL aceptar un campo opcional `crop` = `[x1, y1, x2, y2]`, normalizado sobre
la imagen de la captura ya orientada, con `0 ≤ x1 < x2 ≤ 1` y `0 ≤ y1 < y2 ≤ 1`. Con `crop`, las cajas
de `detections` SHALL interpretarse sobre la región recortada; sin `crop`, sobre la imagen entera. El
backend SHALL guardar el recorte con el diagnóstico y NO SHALL modificar el JPEG de la captura.

#### Scenario: Alta con recorte
- **WHEN** el servicio de inferencia registra un diagnóstico con `crop` válido
- **THEN** el backend lo guarda junto al diagnóstico

#### Scenario: Alta sin recorte
- **WHEN** el simulador registra un diagnóstico manual sin `crop`
- **THEN** el alta se acepta como hoy y el diagnóstico queda sin recorte

#### Scenario: Recorte inválido
- **WHEN** el alta trae `crop` con un valor fuera de `[0, 1]`, con `x1 ≥ x2`, o con una cantidad de
  valores distinta de cuatro
- **THEN** el backend responde `400` con un mensaje que nombra el campo `crop` y no registra nada

### Requirement: El recorte se consulta con las detecciones
`GET /api/diagnosticos/{id}/detecciones` SHALL devolver, junto a `detections`, el `crop` del
diagnóstico, o `null` si no tiene.

#### Scenario: Diagnóstico con recorte
- **WHEN** se consultan las detecciones de un diagnóstico registrado con recorte
- **THEN** la respuesta trae `crop` con los cuatro valores guardados

#### Scenario: Diagnóstico anterior a este cambio
- **WHEN** se consultan las detecciones de un diagnóstico registrado antes del cambio
- **THEN** la respuesta trae `crop: null`

### Requirement: El visor muestra la región recortada
El visor de la captura del dashboard SHALL mostrar sólo la región `crop` del JPEG original, en las
tres vistas (Original, Diagnóstico y Todas), y SHALL dibujar las cajas sobre esa región. Sin recorte,
SHALL mostrar la imagen entera, como hasta ahora.

#### Scenario: Diagnóstico del servicio con recorte
- **WHEN** el usuario abre la captura de un diagnóstico con recorte
- **THEN** ve la región recortada, sin el riel ni el piso, con las cajas sobre las hojas

#### Scenario: Diagnóstico sin recorte
- **WHEN** el usuario abre la captura de un diagnóstico manual o anterior al cambio
- **THEN** ve la imagen entera con sus cajas, igual que antes

#### Scenario: Detecciones que no pueden cargarse
- **WHEN** la consulta de detecciones falla
- **THEN** el visor muestra la imagen entera, sin cajas, con el aviso que ya existe
