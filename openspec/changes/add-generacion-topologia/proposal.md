# Change: add-generacion-topologia

## Why

**HU-18 CA-01** pide que el **Administrador**, en la configuración inicial de un vivero
nuevo **sin topología cargada**, defina la estructura física indicando la cantidad de
macro-zonas y de sectores por macro-zona (ej. 10 macro-zonas de 100 sectores cada una), y
que el sistema **genere la grilla lógica**, asigne un identificador único e irrepetible a
cada sector y a cada macro-zona, y deje la disposición disponible para el mapa de
producción.

Hoy la topología es un **seed fijo** en `resources/data.sql` (6 macro-zonas × 100
sectores). No existe ningún generador administrable: las zonas/sectores nunca se crean
desde Java. Este criterio quedó documentado explícitamente como **Non-Goal** del cambio
`add-gestion-hardware` (que trabajó el registro y mapeo de dispositivos *sobre* esa
topología existente). Este cambio **completa ese Non-Goal**: agrega la generación dinámica
de la topología, full-stack, manteniendo el seed demo para que la app siga arrancando con
datos listos.

## What Changes

- **Generación de topología en el backend**: nuevo `POST /api/topologia` que recibe
  `{ macroZonas, sectoresPorMacroZona }` y crea las `ZonaEntity` (`MZ-1`..`MZ-N`) y
  `SectorEntity` (`MZ-{z}-{NNN}`) con identificadores únicos y los **defaults offline**
  idénticos al seed. `GET /api/topologia` devuelve el resumen de la topología actual.
- **Semántica generar-vacío vs. regenerar** (decisión de alcance): si el vivero está vacío,
  genera; si ya hay topología, **rechaza con 409** salvo que el request traiga
  `regenerar=true`, que borra (cascade) zonas/sectores + dispositivos + historial asociados
  y recrea la grilla. Evita wipes accidentales.
- **Validación de rangos**: `macroZonas` y `sectoresPorMacroZona` deben ser positivos y
  dentro de límites operativos; un valor inválido responde 400.
- **Conteo de sectores dinámico**: `NurseryService.getSnapshot()` dejaba el total de
  sectores hardcodeado en `600`; pasa a derivarse del conteo real para que el dashboard sea
  correcto tras regenerar.
- **Capa de datos del frontend**: `DataRepository` gana `getTopologia()` y
  `generarTopologia()`; mock determinístico (con validación que espeja al backend) y cliente
  HTTP. Los generadores del mapa mock pasan a leer la topología en vez de constantes fijas,
  para que la grilla generada se refleje en el mapa de producción.
- **Vista de Administración de topología**: nueva página con resumen de la topología actual
  y formulario (N macro-zonas × M sectores) con confirmación explícita al regenerar.
  Ruta + ítem de navegación.

## Impact

- Affected specs: `topologia-persistencia` (backend, nueva), `gestion-topologia`
  (frontend, nueva).
- Affected code:
  - Backend `Desarrollo/backend/`: nuevos `dto/NuevaTopologia`, `dto/TopologiaVivero`,
    `service/TopologiaService`, `service/TopologiaInvalidaException`,
    `service/TopologiaConflictoException`, `controller/TopologiaController`; edición de
    `service/NurseryService` (conteo de sectores dinámico). Reutiliza `model/ZonaEntity`,
    `model/SectorEntity`, `repository/ZonaRepository`, `repository/SectorRepository`,
    `repository/DispositivoRepository`, `repository/HistorialRepository`.
  - Frontend `Desarrollo/frontend/`: nuevos `features/topologia/`,
    `data/mock/topologia.ts`, `hooks/useTopologia.ts`; edición de `types/domain.ts`,
    `data/repository.ts`, `data/http/httpRepository.ts`, `data/mock/mockRepository.ts`,
    `data/mock/generators.ts`, `data/mock/hardware.ts`, `router.tsx`,
    `components/layout/Sidebar.tsx`.
- No toca Modelo_IA.

## Non-Goals

- **Gating por rol** (solo Administrador): depende de HU-01/HU-20 (autenticación
  inexistente hoy); la vista se expone sin auth, consistente con el resto de la app.
- **Topología no uniforme** (cantidad de sectores variable por macro-zona): HU-18 CA-01
  modela una grilla uniforme (N × M); sectores heterogéneos por zona es un cambio posterior.
- **Persistencia de la topología generada entre reinicios sin tocar el seed**: con el seed
  demo activo (`spring.sql.init.mode=always`), un reinicio re-inserta las zonas/sectores
  demo que falten. Se documenta el trade-off y la mitigación (`spring.sql.init.mode=never`)
  en `design.md`; cambiarlo es una decisión operativa, fuera del core de este cambio.
- **Sincronización física con el hardware** y re-mapeo automático de dispositivos tras una
  regeneración: la regeneración limpia los dispositivos para no dejar referencias colgantes;
  volver a registrarlos es el flujo de `add-gestion-hardware`.
