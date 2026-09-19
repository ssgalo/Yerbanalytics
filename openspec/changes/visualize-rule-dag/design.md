# Design: visualize-rule-dag

## Architecture Overview
La arquitectura minimiza el acoplamiento y el consumo de base de datos delegando la renderización de estado al Frontend. 

El proceso es el siguiente:
1. El usuario entra a la vista de historial de un sector.
2. El Frontend carga el historial (`GET /api/v1/historial/...`) y, paralelamente, el esquema del motor (`GET /api/v1/rules/schema`).
3. El esquema contiene la lista ordenada de Nodos (Reglas) y cómo están interconectados.
4. El usuario hace click en un evento específico (Ej: "Riego abortado por Clima"). El objeto evento contiene `lectura: "Ciclo de evaluación: ClimaOverrideRule."` o `tipo: "Info"`.
5. El componente `RuleGraph` (basado en `React Flow`) recorre los nodos del esquema:
   - Identifica el nodo que causó la detención/acción (haciendo un matching del ID/Label de la regla con el string del evento).
   - Colorea los nodos anteriores (que permitieron que el flujo avance) de **Verde**.
   - Colorea el nodo detonante de **Rojo/Amarillo** (si bloqueó) o de **Azul/Verde Fuerte** (si generó la acción física).
   - Los nodos con prioridad mayor al detonante (que nunca se ejecutaron) se pintan de **Gris oscuro/inhabilitado**.

## API Design & Payload Structure

### `GET /api/v1/rules/schema`
Este endpoint iterará sobre `RuleOrchestrator.getRules()` y construirá el grafo genérico.

**Formato de Respuesta:**
```json
{
  "nodes": [
    { "id": "start", "label": "Inicio Evaluación", "type": "input", "priority": -1 },
    { "id": "BloqueoManualRule", "label": "BloqueoManualRule", "type": "default", "priority": 0 },
    { "id": "StaleSensorRule", "label": "StaleSensorRule", "type": "default", "priority": 1 },
    { "id": "ClimaOverrideRule", "label": "ClimaOverrideRule", "type": "default", "priority": 2 },
    { "id": "abort", "label": "Fin (Abortado)", "type": "output", "priority": 999 },
    { "id": "success", "label": "Fin (Evaluado OK)", "type": "output", "priority": 999 }
  ],
  "edges": [
    { "id": "e_start_Bloqueo", "source": "start", "target": "BloqueoManualRule" },
    { "id": "e_Bloqueo_Stale", "source": "BloqueoManualRule", "target": "StaleSensorRule", "label": "Continúa" },
    { "id": "e_Bloqueo_abort", "source": "BloqueoManualRule", "target": "abort", "label": "Bloquea" },
    { "id": "e_Stale_Clima", "source": "StaleSensorRule", "target": "ClimaOverrideRule", "label": "Continúa" },
    { "id": "e_Stale_abort", "source": "StaleSensorRule", "target": "abort", "label": "Bloquea" }
  ]
}
```
*Detalle Backend:* El controlador no debe tener las reglas hardcodeadas. Debe recibir `List<Rule> rules` por Inyección de Dependencias, ordenarlas, y generar secuencialmente las aristas de "Continúa" al siguiente nodo, y las aristas de "Bloquea" apuntando al nodo de salida común de aborto.

## Frontend UI/UX Design

### Librería y Dependencias
Se debe utilizar **React Flow** (`npm install reactflow`). Es el estándar de la industria, soporta drag & drop, zooming, panning y es muy flexible con los estilos.

### Layout (Vista de Historial)
- **Timeline Lado Izquierdo (30% de pantalla):** Una lista vertical cronológica con los eventos del historial.
- **Canvas Lado Derecho (70% de pantalla):** El componente `React Flow`.

### Lógica de Estilizado Dinámico (`RuleGraph.tsx`)
Cuando el prop `activeEvent` cambia:
1. Extraer la regla de `activeEvent.lectura` (Ej: usando Regex `Ciclo de evaluación:\s*(\w+Rule)`).
2. Si es una acción exitosa (ej `tipo: 'Riego'` o `tipo: 'Insumo'`), se asume que llegó hasta la regla `RiegoRule` o `InsumoRule`.
3. Iterar los `nodes` proporcionados por el backend:
   - `if (node.priority < detonantePriority)` -> `style: { background: '#e8f5e9', border: '1px solid #4caf50' }` (Pasó)
   - `if (node.id === detonanteId)` -> Si es error `style: { background: '#ffebee', border: '1px solid #f44336' }`, si es info `style: { background: '#fff3e0', border: '1px solid #ff9800' }`.
   - `if (node.priority > detonantePriority)` -> `style: { background: '#f5f5f5', border: '1px solid #e0e0e0', color: '#9e9e9e' }` (Skip / No evaluado).
4. Actualizar el estado de React Flow para gatillar el re-render.
