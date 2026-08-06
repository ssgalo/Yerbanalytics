## ADDED Requirements

### Requirement: Camino único de alta de diagnósticos
El sistema SHALL exponer un único endpoint público para dar de alta un diagnóstico, y ese
endpoint SHALL ser el mismo que use cualquier emisor, sea el servicio de inferencia o una carga
manual. NO SHALL existir un endpoint alternativo, un parámetro ni una variante de
comportamiento destinada a distinguir el origen del diagnóstico.

#### Scenario: Un solo camino de escritura
- **WHEN** se audita cómo se crean los diagnósticos en el sistema
- **THEN** existe un único endpoint de alta, sin variantes ni duplicados

#### Scenario: Las validaciones no dependen del emisor
- **WHEN** dos altas idénticas llegan desde emisores distintos
- **THEN** el sistema aplica exactamente las mismas validaciones y produce exactamente el mismo
  resultado

#### Scenario: El alta no depende del modo de operación
- **WHEN** se da de alta un diagnóstico con el vivero en modo estático o en modo simulación
- **THEN** el resultado es el mismo, porque el modo de operación gobierna la simulación de
  telemetría y no el alta de diagnósticos

### Requirement: Diagnóstico anclado a una captura
Todo diagnóstico SHALL estar vinculado a una captura existente. El vínculo SHALL ser
obligatorio, porque todo diagnóstico nace del análisis de una imagen. Un diagnóstico SHALL
componerse de sector, macro-zona, captura, estado, nivel de confianza y severidad.

#### Scenario: Alta con su captura
- **WHEN** se da de alta un diagnóstico referenciando una captura existente
- **THEN** el diagnóstico queda persistido con el vínculo a esa captura

#### Scenario: Alta sin captura
- **WHEN** se intenta dar de alta un diagnóstico sin referenciar una captura
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: Captura inexistente
- **WHEN** el diagnóstico referencia una captura que no existe
- **THEN** el sistema lo rechaza con `400` y no lo registra

### Requirement: Validación de los datos del diagnóstico
El sistema SHALL validar que el estado pertenezca a la taxonomía de diagnósticos de la
plataforma, que el nivel de confianza sea un porcentaje entre 0 y 100, y que el sector exista
en la topología vigente.

#### Scenario: Confianza fuera de rango
- **WHEN** el nivel de confianza está fuera del rango de 0 a 100
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: Sector inexistente
- **WHEN** el diagnóstico referencia un sector que no existe en la topología vigente
- **THEN** el sistema lo rechaza con `400` y no lo registra

#### Scenario: Estado fuera de la taxonomía
- **WHEN** el estado diagnosticado no pertenece a la taxonomía de la plataforma
- **THEN** el sistema lo rechaza con `400` y no lo registra

### Requirement: Umbral de confianza para diagnósticos concluyentes
Un diagnóstico cuyo nivel de confianza no alcance el umbral mínimo configurado SHALL marcarse
como no concluyente.

#### Scenario: Confianza suficiente
- **WHEN** el nivel de confianza alcanza o supera el umbral mínimo
- **THEN** el diagnóstico queda marcado como concluyente

#### Scenario: Confianza insuficiente
- **WHEN** el nivel de confianza queda por debajo del umbral mínimo
- **THEN** el diagnóstico queda marcado como no concluyente

### Requirement: Los diagnósticos registrados integran el snapshot del vivero
El snapshot del vivero que consume el dashboard SHALL incluir los diagnósticos registrados
junto con los diagnósticos que ya deriva de los sectores, sin que la vista deba distinguirlos.
Los diagnósticos registrados SHALL aparecer primero, SHALL contarse en las estadísticas de
diagnósticos y SHALL exponer la URL de la imagen de su captura. Los diagnósticos derivados de
sectores SHALL exponer esa URL vacía.

#### Scenario: Un diagnóstico registrado aparece en el snapshot
- **WHEN** se registra un diagnóstico y luego se consulta el snapshot del vivero
- **THEN** el diagnóstico figura en la lista de diagnósticos, con su URL de imagen

#### Scenario: Los identificadores no colisionan
- **WHEN** conviven diagnósticos registrados y diagnósticos derivados de sectores
- **THEN** cada uno conserva un identificador propio e irrepetible y el índice por
  identificador los resuelve a todos

#### Scenario: El conteo refleja los registrados
- **WHEN** se registra un diagnóstico nuevo
- **THEN** el contador de diagnósticos del snapshot lo incluye

#### Scenario: Los diagnósticos derivados no traen imagen
- **WHEN** un diagnóstico proviene del estado del sector y no de una captura
- **THEN** su URL de imagen viene vacía y la presentación recurre a su respaldo visual

### Requirement: Listado de diagnósticos registrados
El backend SHALL exponer la consulta de los diagnósticos registrados, ordenados del más
reciente al más antiguo, incluyendo su captura asociada.

#### Scenario: Consulta del listado
- **WHEN** se consultan los diagnósticos registrados
- **THEN** la respuesta los devuelve del más reciente al más antiguo, con su captura asociada
