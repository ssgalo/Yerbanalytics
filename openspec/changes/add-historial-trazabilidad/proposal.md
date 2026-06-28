# Change: add-historial-trazabilidad

## Why

La sección **Historial** es hoy un placeholder (`/historial` → `PlaceholderPage`).
El Product Backlog la define en dos historias de prioridad media:

- **HU-11** — el Productor Viverista quiere consultar el **historial completo de
  cada acción ejecutada y su justificación** (cadena `lectura/diagnóstico IA →
  decisión del motor → acción física`), filtrable por sector/macro-zona, tipo de
  acción y rango de fechas, y **inalterable** (sin editar ni eliminar) para
  respaldar controles de calidad y certificaciones.
- **HU-12** — quiere ver la **evolución del sector luego de un tiempo prudencial
  post-acción** para evaluar la efectividad real de la intervención (delta
  antes/ahora, latencia, veredicto `Efectiva` / `Sin efectividad`).

Hoy el backend no persiste nada de esto: `NurseryService.getSnapshot()` reconstruye
la lista de acciones desde plantillas hardcodeadas (`ACT_TPL`) en cada llamada, y
`updateTelemetry()` decide actuadores pero **no registra ningún evento**. El feed
del frontend (`NurseryData.actions`) es decorativo y se pierde en cada ciclo. Sin
un registro persistente no hay trazabilidad ni auditoría posible.

## What Changes

- **Persistencia de auditoría** en el backend: nueva entidad inmutable
  `historial_evento` que guarda cada acción ejecutada con su cadena de
  justificación y los parámetros físicos (duración/volumen/dosis).
- **Hook de registro** en la lógica de actuación de `NurseryService.updateTelemetry()`:
  cuando un actuador pasa a estado activo (electroválvula → `Regando`, bomba →
  `Dosificando`, mediasombra ajustada) se persiste un evento de historial.
- **Motor de seguimiento post-acción** (`@Scheduled`): tras vencer la latencia
  configurada, toma una nueva lectura del sector, calcula el delta, fija el
  veredicto y bloquea la repetición autónoma si la acción no fue efectiva (HU-12).
- **Endpoint** `GET /api/historial` con filtros opcionales (`sector`, `zona`,
  `tipo`, `desde`, `hasta`), inmutable (sin PUT/DELETE).
- **Capa de datos del frontend**: `DataRepository` gana `getHistory()`; mock
  determinístico (RNG sembrado) y cliente HTTP.
- **Vista Historial** real: timeline global de acciones con su cadena de
  justificación expandible y la evolución post-acción, más barra de filtros
  (tipo / macro-zona / sector / rango de fechas / resultado). Reemplaza el
  placeholder de `/historial`.

## Impact

- Affected specs: `historial-trazabilidad` (frontend, nueva),
  `historial-persistencia` (backend, nueva).
- Affected code:
  - Backend `Desarrollo/backend/`: nuevos `model/HistorialEventoEntity`,
    `repository/HistorialRepository`, `dto/HistorialEvento` + `dto/Evolution`,
    `service/HistorialService`, `controller/HistorialController`; edición de
    `service/NurseryService` (hook) y `resources/data.sql` (seed).
  - Frontend `Desarrollo/frontend/`: nuevos `features/historial/`, generador mock,
    hook `useHistory`; edición de `types/domain.ts`, `data/repository.ts`,
    `data/http/httpRepository.ts`, `data/mock/`, `router.tsx`.
- No toca Modelo_IA. El placeholder de `/configuracion` y `/hardware` se conserva.

## Non-Goals

- **HU-16** (exportar reportes PDF/CSV): queda fuera de este cambio; el endpoint de
  consulta deja la puerta abierta pero la exportación es un cambio posterior.
- No se implementa la latencia biológica real por tipo de acción configurable por
  el agrónomo (HU-15); se usa una latencia por defecto demo.
- No se reescribe el feed de actividad del dashboard (`NurseryData.actions`); sigue
  como está y se complementa con el historial persistente.
