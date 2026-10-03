## MODIFIED Requirements

### Requirement: Umbrales activos en el motor
El backend SHALL usar los umbrales persistidos de `umbral_metrica` para calcular el estado de las
métricas de cada sector. El disparo del riego SHALL depender del parámetro de catálogo
`riego.umbral-humedad` y NO de la banda `idealMin` de `humSus`.

#### Scenario: Cambiar una banda altera el estado de los sectores
- **WHEN** se guarda una nueva banda `ideal`/`warn` para una métrica
- **THEN** el cálculo de estado de los sectores en `GET /api/nursery` refleja la nueva banda en las
  lecturas siguientes

#### Scenario: Cambiar una banda no altera el riego
- **WHEN** se guarda una nueva banda `ideal` para `humSus`
- **THEN** la regla de riego sigue usando el valor vigente de `riego.umbral-humedad`

### Requirement: Persistencia de la configuración
El backend SHALL persistir la configuración agronómica editable: las bandas de cada métrica, los
límites operativos que no son umbrales de reglas (volumen máximo diario de riego y dosis máxima de
insumo), las etapas del plan de rustificación y los parámetros de seguimiento. El tiempo máximo de
apertura de riego y la apertura máxima de mediasombra SHALL persistirse en el catálogo de
parámetros de reglas y NO SHALL formar parte de `configuracion_operativa`.

#### Scenario: Campo mudado al catálogo
- **WHEN** se consulta `GET /api/configuracion`
- **THEN** la respuesta no incluye `riegoTiempoMaxSeg` ni `mediasombraAperturaMaxPct`

#### Scenario: Rustificación validada contra el catálogo
- **WHEN** se guarda una etapa de rustificación con apertura mayor a `mediasombra.apertura-maxima`
- **THEN** el backend responde 400 sin persistir
