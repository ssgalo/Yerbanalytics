# Change: add-bloqueo-manual

> Rama: `feat/reglas-v2-bloqueo-manual`. Cubre **S-01** (`reglas_v2` §4) y HU-19. Índice: `reglas-v2-overview/README.md`.
> **Depende de:** `add-control-demo-reglas`.

## Why

`ManualLockRule` (prioridad 0) ya corta toda la cadena cuando `RuleContext.bloqueoManualActivo()` es
verdadero, y existe la tabla `bloqueo_manual` (`ManualLockEntity`). Pero **no hay forma de crear un bloqueo**:
la búsqueda en los controladores del backend no encuentra ningún endpoint de bloqueos (sólo aparece la
palabra en `RuleEngineSchemaController`), aunque la spec viva `openspec/specs/bloqueo-manual/` ya pide
`GET/POST /api/bloqueos` y `DELETE /api/bloqueos/{id}`. La v2 §13 dice además que el front no tiene la función.
Resultado: S-01 está implementada en el motor y es **inalcanzable** para un operario.

## What Changes

- API de bloqueos (`GET/POST /api/bloqueos`, `DELETE /api/bloqueos/{id}`) con su permiso en `MapaPermisos`.
- **Alcance sector o macro-zona**; con bloqueo de MZ la mediasombra queda quieta (v2 S-01).
- Alertas `INFO` al activar y al reanudar; al reanudar se evalúa con la telemetría actual **sin acciones
  retroactivas** (HU-19 CA-04).
- Bloqueo de bombas y válvulas ya en curso: al activar, se ordena **cierre inmediato** del riego y las bombas
  del alcance.
- Frontend: botón Bloquear/Reanudar en el detalle de sector y de macro-zona, con motivo obligatorio y
  listado de bloqueos activos.

## Out of scope

- Cambiar `ManualLockRule` más allá de lo que pida el alcance de MZ (ver design D-2).
- Roles nuevos: el permiso se asigna a Operario en la matriz sembrada por defecto (`MapaPermisos`/`Permiso`).
