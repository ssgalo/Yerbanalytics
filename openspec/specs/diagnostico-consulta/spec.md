# diagnostico-consulta

> **Estado de implementación:** ✅ Implementado completamente.
> Frontend: `DiagnosticsPage`, `DiagCard` (barra de confianza, badge de severidad), `DiagFilters` (por estado y severidad), `PhotoModal` (imagen cenital a tamaño completo, cierre con Escape/clic fuera). Integrado con `useNurseryData().diagnoses`.

## Purpose

Vista de consulta de diagnósticos de IA con nivel de confianza y severidad (HU-05).
Permite al productor viverista consultar todos los diagnósticos registrados,
filtrarlos por estado y severidad, ver el nivel de confianza del modelo y acceder
a la imagen cenital que originó el diagnóstico.

## Requirements

### Requirement: Listado de diagnósticos con confianza y severidad

La vista Diagnósticos SHALL mostrar todos los diagnósticos registrados, cada uno
con: estado diagnosticado (hongos, clorosis, plagas, estrés solar, saludable),
nivel de severidad (crítica / advertencia / saludable), nivel de confianza del
modelo expresado como porcentaje y barra visual, sector y macro-zona de origen,
y antigüedad del diagnóstico. La lista SHALL ordenarse del diagnóstico más reciente
al más antiguo.

#### Scenario: Diagnóstico con confianza visible

- **WHEN** el productor abre `/diagnosticos`
- **THEN** ve cada diagnóstico con su estado, severidad, barra de confianza y
  sector de origen, ordenados del más reciente al más antiguo

#### Scenario: Lista vacía tras filtrado

- **WHEN** los filtros aplicados no coinciden con ningún diagnóstico
- **THEN** la vista muestra un estado vacío indicando que no hay diagnósticos
  que coincidan con los filtros

### Requirement: Indicador de confianza del modelo

Cada diagnóstico SHALL mostrar el nivel de confianza del modelo IA con una barra
de progreso visual. Cuando la confianza alcanza o supera el umbral de actuación
(85%), la vista SHALL indicarlo con un aviso de que el motor puede actuar sobre
ese sector.

#### Scenario: Confianza >= 85% — aviso de actuación posible

- **WHEN** la confianza del diagnóstico es ≥ 85%
- **THEN** la card del diagnóstico muestra el badge de confianza en verde y un
  indicador de que el motor de reglas puede activar acciones sobre ese sector

#### Scenario: Confianza < 85% — diagnóstico no concluyente para actuación

- **WHEN** la confianza del diagnóstico es < 85%
- **THEN** la card muestra la barra de confianza sin el indicador de actuación,
  indicando que el diagnóstico es referencial pero no gatilla acciones autónomas

### Requirement: Filtros por estado y severidad

La vista SHALL ofrecer filtros por estado diagnosticado y por severidad, con un
contador del número de diagnósticos que cumplen los criterios.

#### Scenario: Filtrar por estado

- **WHEN** el usuario selecciona el estado "Hongos"
- **THEN** la lista muestra solo los diagnósticos con ese estado diagnosticado

#### Scenario: Filtrar por severidad

- **WHEN** el usuario selecciona severidad "Crítica"
- **THEN** la lista muestra solo los diagnósticos de severidad crítica

#### Scenario: Filtrar por ambos criterios

- **WHEN** el usuario selecciona estado "Clorosis" y severidad "Advertencia"
- **THEN** la lista muestra solo los diagnósticos que cumplen ambos criterios

### Requirement: Acceso a la imagen cenital

Cada diagnóstico SHALL ofrecer acceso a la imagen cenital que capturó el sector y
que el modelo analizó. Al hacer clic en la miniatura, la imagen SHALL desplegarse
a tamaño completo en un modal. El modal SHALL cerrarse con Escape, con su botón de
cierre y haciendo clic fuera de la imagen.

#### Scenario: Ampliar imagen cenital

- **WHEN** el productor hace clic en la miniatura de un diagnóstico
- **THEN** la imagen se muestra a tamaño completo, identificada con el sector, el
  estado diagnosticado y la antigüedad del diagnóstico

#### Scenario: Cerrar el modal de imagen

- **WHEN** el usuario presiona Escape, usa el botón de cierre o hace clic fuera
  de la imagen
- **THEN** el modal se cierra y el usuario regresa a la lista de diagnósticos

#### Scenario: Diagnóstico sin imagen

- **WHEN** un diagnóstico no tiene imagen asociada (diagnóstico derivado del estado
  del sector, no de una captura)
- **THEN** la card no muestra miniatura ni botón de ampliar

### Requirement: Diagnósticos del snapshot del vivero integrados

La vista SHALL mostrar tanto los diagnósticos registrados (con imagen) como los
derivados del estado actual de los sectores, sin que el usuario deba distinguirlos.
Los registrados SHALL aparecer primero y SHALL exponer la URL de su imagen. Los
derivados de sectores SHALL mostrar su respaldo visual alternativo.

#### Scenario: Diagnósticos registrados y derivados conviven

- **WHEN** existen diagnósticos registrados y sectores con estado diagnosticado
- **THEN** la vista los muestra en el mismo listado, con los registrados primero

### Requirement: Origen de datos por entorno

La vista SHALL obtener los diagnósticos a través de `DataRepository`, sin conocer
el origen concreto (mock o backend HTTP).

#### Scenario: Modo backend

- **WHEN** `VITE_DATA_SOURCE=http`
- **THEN** los diagnósticos se obtienen del snapshot de `GET {VITE_API_BASE_URL}/nursery`
  y de `GET {VITE_API_BASE_URL}/diagnosticos`

#### Scenario: Modo mock

- **WHEN** `VITE_DATA_SOURCE` no está definida o vale `mock`
- **THEN** los diagnósticos provienen del mock determinístico
