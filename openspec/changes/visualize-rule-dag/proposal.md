# Change: visualize-rule-dag

## Context & Motivation (Why)
Actualmente, el motor de reglas (`RuleOrchestrator`) evalúa de manera asíncrona y continua las condiciones climáticas, la telemetría de humedad y bloqueos manuales. Tras cada ciclo, el sistema genera eventos de acción física (`ACTIVAR_VALVULA`, `ACTIVAR_BOMBA`) o registros de "Inacción" (ej: *Ciclo de evaluación: ClimaOverrideRule.*), los cuales se persisten a través de `HistorialService`.
En el Frontend, estos registros se muestran como una línea de tiempo plana y secuencial. Para un agrónomo o un operador de vivero, comprender **por qué** el sistema no regó a las 14:00 hrs exige un esfuerzo cognitivo para leer el texto, entender qué regla falló y cómo eso cortó el flujo.
Para mejorar radicalmente la explicabilidad (Explainable AI / XAI), implementaremos la visualización gráfica del proceso de decisión. Utilizaremos un Grafo Dirigido Acíclico (DAG) interactivo, donde el usuario pueda ver visualmente el pipeline, qué reglas pasaron con éxito (verde), qué regla interrumpió el flujo (rojo) y cuáles no se llegaron a evaluar (gris).

## What Changes
1. **Backend (API de Topología Dinámica)**: 
   En lugar de hardcodear la topología del DAG en el frontend o guardar JSONs enormes en la base de datos por cada evento, el Backend expondrá un endpoint estático `/api/v1/rules/schema`. Este endpoint inspeccionará las `Rule` autodescubiertas por el `RuleOrchestrator` (basado en sus prioridades) y devolverá la estructura base (Nodos y Aristas).
2. **Frontend (Reconstrucción del Estado en Cliente)**: 
   Un nuevo componente en el Dashboard Sectorial ("Inspector de Decisiones") que utilizará `React Flow`. 
   Al seleccionar un evento del historial, el Frontend cruzará la cadena de texto de la regla responsable (presente en el campo `lectura` o `tipo` del `HistorialEvento`) contra la topología estática devuelta por el Backend, para colorear el DAG y mostrar el camino exacto que tomó la ejecución.

## Impact
- **Affected specs:** `motor-reglas` (complementa la parte visual).
- **Affected code:** 
  - **Backend**: `RuleEngineSchemaController.java`, DTOs de topología (`RuleNodeDto`, `RuleEdgeDto`).
  - **Frontend**: Nuevo directorio `src/components/DAGViewer/`, actualización en `SectorHistorial` para incluir layout partido (Timeline / Grafo).
- **Database:** **Cero impacto.** La persistencia actual (`historial_evento`) proporciona toda la información necesaria (el nombre de la regla que actuó o abortó) sin necesidad de agregar columnas complejas de trazas.

## Non-Goals
- **Modificación de la lógica Agronómica**: No se alterará ninguna decisión ni el `RuleOrchestrator`. 
- **Persistencia de Trazas**: No se serializarán grafos en PostgreSQL ni Redis, se infieren dinámicamente.
