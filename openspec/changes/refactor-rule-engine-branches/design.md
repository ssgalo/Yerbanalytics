# Design: Motor de Reglas en Ramas (DAG Independiente)

## 1. Backend: Domain Modeling (`RuleBranch`)
Se introduce el concepto de "Rama de Regla". Cada regla declarará explícitamente a qué rama pertenece.

```java
public enum RuleBranch {
    GLOBAL, RIEGO, INSUMO, MEDIASOMBRA, SEGUIMIENTO
}
```

La interfaz `Rule` incorpora este concepto con un default a `GLOBAL`:
```java
default RuleBranch branch() { return RuleBranch.GLOBAL; }
```

### Mapeo de Reglas Existentes
- `ManualLockRule` -> `GLOBAL`
- `StaleSensorRule` -> `GLOBAL`
- `ClimaOverrideRule` -> `RIEGO`
- `DosisLimiteRule` -> `RIEGO`
- `RiegoRule` -> `RIEGO`
- `DailyDoseLimitRule` -> `INSUMO`
- `InsumoRule` -> `INSUMO`
- `MediasombraRule` -> `MEDIASOMBRA`
- `ShadingRule` -> `MEDIASOMBRA`
- `SeguimientoRule` / `FollowUpRule` -> `SEGUIMIENTO`

## 2. Backend: `RuleOrchestrator`
El orquestador mantendrá la iteración secuencial (basada en el orden de prioridad), pero en lugar de abortar toda la ejecución al primer `isBlocking()`, registrará en variables locales si una rama específica fue bloqueada.
```java
boolean abortAll = false;
boolean abortRiego = false;
boolean abortInsumo = false;
// ...
```
Al evaluar la siguiente regla, si su rama correspondiente ya fue bloqueada, simplemente se omite y la evaluación continúa con las reglas de otras ramas.

## 3. Backend & Frontend: `DagSchemaDto` y Renderizado
El controlador que expone `/api/rules/schema` construirá un DAG con verdadera ramificación (múltiples salidas de un solo nodo).
- **Global:** Todos los nodos `GLOBAL` están en cadena única. El último apunta al inicio de cada una de las otras ramas.
- **Ramas Locales:** Cada rama (`RIEGO`, `INSUMO`, etc.) forma una cadena independiente con su propio nodo de éxito (`success-riego`, `success-insumo`, etc.).
- **Bloqueos:** Los nodos bloqueantes apuntan al nodo abort general (`abort`) o al abort local de la rama (`abort-riego`, etc.).

En el frontend, se modifica `domain.ts` para agregar `branch: string` a `RuleNode`. `RuleGraph.tsx` usará este campo para posicionar las ramas horizontalmente, en columnas.
