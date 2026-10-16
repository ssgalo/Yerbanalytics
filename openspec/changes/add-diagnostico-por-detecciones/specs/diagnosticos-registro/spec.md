## MODIFIED Requirements

### Requirement: Diagnóstico anclado a una captura
Todo diagnóstico SHALL estar vinculado a una captura existente. El vínculo SHALL ser
obligatorio, porque todo diagnóstico nace del análisis de una imagen. Un diagnóstico SHALL
componerse de sector, macro-zona, captura, estado, nivel de severidad (sólo en estados anómalos),
nivel de confianza (salvo en No concluyente), score promedio del estado diagnosticado expresado como
porcentaje (salvo en No concluyente) y las detecciones que intervinieron, que pueden faltar en una
carga manual.

#### Scenario: Alta con su captura
- **WHEN** se da de alta un diagnóstico referenciando una captura existente
- **THEN** el diagnóstico queda persistido con el vínculo a esa captura

#### Scenario: Alta sin captura
- **WHEN** se intenta dar de alta un diagnóstico sin referenciar una captura
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: Captura inexistente
- **WHEN** el diagnóstico referencia una captura que no existe
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: Carga manual sin detecciones
- **WHEN** se da de alta un diagnóstico válido sin detecciones
- **THEN** el diagnóstico queda persistido con cero detecciones

### Requirement: Validación de los datos del diagnóstico
El sistema SHALL validar que el estado pertenezca a la taxonomía de diagnósticos de la
plataforma y que el sector exista en la topología vigente. SHALL validar además que los niveles
de severidad y de confianza, cuando vengan, sean `Baja`, `Media` o `Alta`; que el score sea un
porcentaje entre 0 y 100; y la coherencia con el estado: un estado anómalo SHALL traer severidad,
confianza y score; Sano SHALL traer confianza y score y NO SHALL traer severidad; No concluyente NO
SHALL traer severidad, confianza ni score. Cada detección SHALL tener una clase de la taxonomía, un
score entre 0 y 1 y una caja de cuatro coordenadas normalizadas en `[0, 1]` con `x1 < x2` e
`y1 < y2`; un alta SHALL admitir como máximo 2000 detecciones.

#### Scenario: Score fuera de rango
- **WHEN** el score está fuera del rango de 0 a 100
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: Sector inexistente
- **WHEN** el diagnóstico referencia un sector que no existe en la topología vigente
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: Estado fuera de la taxonomía
- **WHEN** el estado diagnosticado no pertenece a la taxonomía de la plataforma
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: Estado anómalo sin severidad
- **WHEN** se da de alta Clorosis sin nivel de severidad
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: Sano con severidad
- **WHEN** se da de alta Sano con un nivel de severidad
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: No concluyente con confianza
- **WHEN** se da de alta No concluyente con nivel de confianza o score
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: Nivel desconocido
- **WHEN** el nivel de confianza no es `Baja`, `Media` ni `Alta`
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: Caja inválida
- **WHEN** una detección trae una caja con coordenadas fuera de `[0, 1]` o con `x1 ≥ x2`
- **THEN** el sistema rechaza el alta completa con `400` y no la registra

## REMOVED Requirements

### Requirement: Umbral de confianza para diagnósticos concluyentes
**Reason**: Que un diagnóstico sea concluyente ya no depende de un umbral global sobre el porcentaje
de confianza (un parámetro del motor), sino del módulo de diagnóstico, que emite No concluyente
cuando no puede concluir. Qué hace el motor con la confianza es decisión del motor.
**Migration**: Ver el requisito "Diagnóstico concluyente". El parámetro
`diagnostico.confianza-minima` sigue existiendo y lo sigue usando `SupplyRule`; sólo deja de decidir
la marca de concluyente.

## ADDED Requirements

### Requirement: Diagnóstico concluyente
Un diagnóstico SHALL marcarse como concluyente si y sólo si su estado es distinto de No concluyente.

#### Scenario: Diagnóstico anómalo con confianza baja
- **WHEN** se registra Clorosis con confianza Baja
- **THEN** el diagnóstico queda marcado como concluyente

#### Scenario: No concluyente
- **WHEN** se registra un diagnóstico No concluyente
- **THEN** el diagnóstico queda marcado como no concluyente

### Requirement: Detecciones de un diagnóstico
El backend SHALL persistir con cada diagnóstico sus detecciones y su cantidad, y SHALL exponerlas en
`GET /api/diagnosticos/{id}/detecciones` con el permiso `diagnosticos.ver`. El listado de
diagnósticos y el snapshot del vivero SHALL informar la cantidad de detecciones y el nivel de
confianza de cada diagnóstico, pero NO SHALL incluir las detecciones.

#### Scenario: Consulta de detecciones
- **WHEN** se consultan las detecciones de un diagnóstico registrado con detecciones
- **THEN** la respuesta trae cada detección con su clase, su score y su caja normalizada

#### Scenario: Diagnóstico inexistente
- **WHEN** se consultan las detecciones de un identificador que no existe
- **THEN** el backend responde `404`

#### Scenario: Snapshot liviano
- **WHEN** se consulta el snapshot del vivero
- **THEN** cada diagnóstico registrado trae su cantidad de detecciones y su nivel de confianza, sin
  las cajas

### Requirement: El diagnóstico vigente del sector incluye su nivel de confianza
El alta de un diagnóstico SHALL actualizar el diagnóstico vigente del sector con su estado, nivel
de severidad, nivel de confianza y score, sin cambiar cómo el motor de reglas lo consume.

#### Scenario: Sector actualizado
- **WHEN** se registra Clorosis, severidad Baja, confianza Media, score 62 para un sector
- **THEN** el diagnóstico del sector en el snapshot muestra Clorosis, Baja, Media y 62
