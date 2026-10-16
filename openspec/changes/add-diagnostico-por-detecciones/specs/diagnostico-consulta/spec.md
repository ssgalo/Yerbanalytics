## MODIFIED Requirements

### Requirement: Listado de diagnósticos con confianza y severidad

La vista Diagnósticos SHALL mostrar todos los diagnósticos registrados, cada uno
con: estado diagnosticado, nivel de severidad (Baja / Media / Alta, sólo en estados
anómalos), nivel de confianza (Baja / Media / Alta, salvo en No concluyente), sector y
macro-zona de origen, y antigüedad del diagnóstico. La lista SHALL ordenarse del diagnóstico
más reciente al más antiguo.

#### Scenario: Diagnóstico con confianza visible

- **WHEN** el productor abre `/diagnosticos`
- **THEN** ve cada diagnóstico con su estado, severidad si corresponde, nivel de confianza y
  sector de origen, ordenados del más reciente al más antiguo

#### Scenario: Lista vacía tras filtrado

- **WHEN** los filtros aplicados no coinciden con ningún diagnóstico
- **THEN** la vista muestra un estado vacío indicando que no hay diagnósticos
  que coincidan con los filtros

### Requirement: Indicador de confianza del modelo

Cada diagnóstico SHALL mostrar su nivel de confianza (Baja, Media o Alta). La vista NO SHALL mostrar
el score promedio ni la cantidad de detecciones: son datos internos del módulo de diagnóstico. La
vista SHALL permitir filtrar y ordenar por nivel de confianza. La vista NO SHALL indicar si el motor de reglas puede
actuar sobre el sector: esa decisión es del motor, no del diagnóstico. Un diagnóstico No concluyente
SHALL mostrarse sin nivel de confianza; un diagnóstico registrado antes de que existieran los
niveles SHALL mostrarse "sin nivel".

#### Scenario: Diagnóstico con nivel de confianza

- **WHEN** la confianza del diagnóstico es Media con score promedio 62 y 14 detecciones
- **THEN** la card muestra "Confianza media", sin el score, sin la cantidad de detecciones y sin aviso de
  actuación

#### Scenario: Diagnóstico No concluyente

- **WHEN** el diagnóstico es No concluyente
- **THEN** la card no muestra nivel de confianza ni severidad

#### Scenario: Filtrar por nivel de confianza

- **WHEN** el usuario filtra la columna Confianza por "Alta"
- **THEN** la grilla muestra sólo los diagnósticos con confianza alta
