# Tasks: visualize-rule-dag

## Phase 1: Backend API
- [ ] **Crear DTOs**: 
  - `com.yerbanalytics.backend.dto.dag.RuleNodeDto` (id, label, type, priority).
  - `com.yerbanalytics.backend.dto.dag.RuleEdgeDto` (id, source, target, label).
  - `com.yerbanalytics.backend.dto.dag.DagSchemaDto` (lista de nodos, lista de aristas).
- [ ] **Crear `RuleEngineSchemaController`**:
  - Inyectar `List<Rule>` y `RuleOrchestrator` si es necesario (para asegurar el orden por `priority()`).
  - Crear endpoint `GET /api/v1/rules/schema`.
- [ ] **Lógica de Generación de Topología**:
  - Nodo "Start".
  - Iterar la lista de reglas `List<Rule>` ordenadas. Por cada regla, crear un `RuleNodeDto`.
  - Crear `RuleEdgeDto` apuntando desde el nodo anterior (o Start) al nodo actual.
  - Crear `RuleEdgeDto` desde el nodo actual al nodo estático "Abort", representando el short-circuit en caso de falla.
  - Devolver el payload JSON consolidado.
- [ ] **Tests de Integración**:
  - Validar que `/api/v1/rules/schema` retorne HTTP 200 y que los nodos estén correctamente encadenados (source de un edge es el target del edge anterior).

## Phase 2: Frontend Foundation
- [ ] **Instalación**: Ejecutar `npm install reactflow` o `npm install @xyflow/react` (según versión) en la carpeta `frontend`.
- [ ] **Servicio de API**: Agregar la llamada `getRuleEngineSchema()` en los clientes HTTP de React para apuntar a `/api/v1/rules/schema`.
- [ ] **Crear UI Base `RuleGraph.tsx`**:
  - Instanciar el lienzo básico de React Flow.
  - Montar los Nodos y Aristas obtenidos del backend.
  - Asegurar un layout decente (se puede utilizar `dagre` para auto-layout jerárquico top-down, o calcular posiciones X e Y multiplicando el índice de la regla).

## Phase 3: Lógica Dinámica y Conexión de Estado
- [ ] **Adaptar `SectorHistorial`**:
  - Cambiar el layout para que el listado de historial actual ocupe una columna izquierda y delegar el evento seleccionado (click) al estado local (`activeEvent`).
- [ ] **Parser de Eventos**:
  - Implementar función auxiliar en Frontend: `extractRuleFromEvent(historialEvent)` que parsee campos como `lectura` o `tipo` y retorne el ID de la regla (ej: `"ClimaOverrideRule"`).
- [ ] **Algoritmo de Coloreado**:
  - En `RuleGraph.tsx`, usar `useEffect` que observe `activeEvent`.
  - Ejecutar el algoritmo que mapee los nodos: Verde (Priority < Activo), Rojo/Amarillo/Azul (Activo), Gris (Priority > Activo).
  - Aplicar estilos de React Flow sobre los Nodos y Aristas (animadas/no animadas) y actualizar el canvas.
- [ ] **Polishing**:
  - Ajustar Tooltips, leyendas y que el gráfico sea Responsivo.
