# Proposal: Refactorizar el Motor de Reglas a Ramas Independientes (DAG)

## Contexto
Actualmente, el `RuleOrchestrator` implementa un **pipeline estrictamente lineal**. Todas las reglas se evalúan en orden de prioridad. Si alguna regla emite una acción bloqueante (ej. `ABORT_RIEGO`), el orquestador hace un `break` en el loop y detiene **toda la evaluación**. 

## Problema
Este diseño produce un comportamiento incorrecto cuando hay subsistemas independientes. Por ejemplo, si el sistema alcanza el límite diario de agua (`DosisLimiteRule`), detiene el pipeline, impidiendo que se evalúe la `MediasombraRule`. La mediasombra no consume agua y debería seguir funcionando independientemente del estado del riego.

## Solución Propuesta
Transformar el pipeline en un modelo de **Ramas Independientes (Sub-cadenas)**:
1. Agrupar las reglas en ramas semánticas: `GLOBAL`, `RIEGO`, `INSUMO`, `MEDIASOMBRA` y `SEGUIMIENTO`.
2. Las reglas globales se evalúan primero y pueden abortar todo el proceso.
3. Las demás ramas se evalúan secuencialmente pero un bloqueo en una rama (ej. `RIEGO`) solo detiene esa rama, permitiendo que las demás (ej. `MEDIASOMBRA`) continúen su ejecución.
4. Exponer esta topología ramificada al frontend a través de `/api/rules/schema` para que el usuario visualice un DAG real con múltiples ramas.
