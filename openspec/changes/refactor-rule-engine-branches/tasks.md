# Tareas: Refactor Rule Engine Branches

- [x] Crear el enum `RuleBranch` en backend.
- [x] Agregar el método `branch()` a la interfaz `Rule`.
- [x] Implementar `branch()` en cada regla concreta según el dominio.
- [x] Modificar `RuleOrchestrator` para mantener el estado de bloqueo de forma granular (`abortRiego`, `abortInsumo`, etc.) en lugar de usar un `break`.
- [x] Actualizar `RuleNodeDto` para incluir el campo `branch`.
- [x] Modificar `RuleEngineSchemaController` para generar un DAG ramificado.
- [x] Actualizar el tipo `RuleNode` en `domain.ts` del frontend.
- [x] Refactorizar el layout de `RuleGraph.tsx` para disponer las ramas horizontalmente utilizando el campo `branch`.
