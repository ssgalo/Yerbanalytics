# Design: add-generacion-topologia

## Context

El backend ya persiste la topología del vivero como `zona` (macro-zona) y `sector`
(`ZonaEntity` 1—N `SectorEntity`, FK real con `orphanRemoval`), pero **estas filas sólo se
crean por el seed** `resources/data.sql` (6 macro-zonas × 100 sectores, todas en estado
`offline`). No hay código Java que cree zonas/sectores: el único `saveAll` sobre sectores
está en el camino de telemetría. `add-gestion-hardware` registró dispositivos *sobre* esa
grilla y dejó la generación dinámica (HU-18 CA-01) como Non-Goal explícito.

El frontend construye el vivero mock con `zonaDefs` (6 entradas en `mock/specs.ts`) y un
bucle fijo `1..100` en `mock/generators.ts`; `mock/hardware.ts` lista las zonas en una
constante `ZONA_IDS`. La capa de datos ya tiene el patrón mock → http por entorno
(`DataRepository`) y vistas con formulario + validación (Hardware, Configuración).

## Goals / Non-Goals

**Goals**
- Que el Administrador genere la grilla lógica (N macro-zonas × M sectores) con
  identificadores únicos e irrepetibles (`MZ-{z}` y `MZ-{z}-{NNN}`), disponible para el
  mapa de producción (HU-18 CA-01).
- Generar sobre un vivero vacío; permitir regenerar de forma explícita y guardada (con
  limpieza de referencias colgantes), evitando wipes accidentales.
- Que el conteo de sectores del dashboard sea real (no un literal `600`).
- Migración mock → http por entorno; contrato JSON idéntico entre DTO backend y tipo
  frontend; los generadores del mapa mock leen la topología.

**Non-Goals**
- Gating por rol (no hay auth) y topología no uniforme (sectores variables por zona).
- Persistir la topología generada entre reinicios sin tocar el seed demo (ver Risks).
- Re-mapeo automático del hardware tras regenerar (se limpia, se vuelve a registrar por la
  HU-18 ya implementada).

## Decisions

### 1. Convención de ids load-bearing: `MZ-{z}` / `MZ-{z}-{NNN}`
La generación respeta el formato de ids existente: macro-zonas `MZ-1`..`MZ-N`, sectores
`MZ-{z}-{NNN}` con `n` zero-padded a 3 dígitos. Es **load-bearing**: `HardwareService.zonaIdDe()`
deriva la zona del sector recortando tras el último `-`, y `dispositivo`/`historial_evento`
referencian zona/sector por string. Mantenerlo evita romper hardware e historial.

### 2. Nombres y sub sintetizados por zona
Como N es dinámico (el seed sólo nombra 6 zonas), la generación sintetiza
`name = "Macro-zona {z}"` y `sub` ciclando `["Sector norte", "Sector centro", "Sector sur"]`
por `(z-1) % 3`, replicando el estilo del seed. El backend (campos `ZonaEntity.name/sub`) y
el mock del frontend usan exactamente la misma regla, para que el contrato no diverja.

### 3. Defaults offline idénticos al seed
Cada sector generado se crea con los mismos defaults que el seed: `status='offline'`,
`color='#A9B2AB'`, `statusLabel='Fuera de servicio'`, `tip='{id} · Fuera de servicio'`,
`reason='Fuera de servicio'`, `diagnosisEstado='Sin diagnóstico'`, `diagnosisConf=null`,
`diagnosisSev='—'`, `actuadorValve='Cerrada'`, `actuadorPump='En espera'`, `actuadorShade=0`,
sensores crudos `null`. Así la grilla nueva se ve igual que la sembrada (offline hasta que
llegue telemetría) y `NurseryService` la deriva sin cambios.

### 4. Semántica generar-vacío vs. regenerar (decisión de alcance)
`POST /api/topologia` recibe `{ macroZonas, sectoresPorMacroZona, regenerar }`:
- Vivero vacío → genera. Fiel a CA-01 ("sin topología cargada").
- Vivero con topología y `regenerar=false` → `TopologiaConflictoException` → **409**
  (no se pisa nada por accidente).
- Vivero con topología y `regenerar=true` → borra `dispositivoRepository.deleteAll()` y
  `historialRepository.deleteAll()` (referencian sector/zona por string, sin FK; quedarían
  colgantes) y `zonaRepository.deleteAll()` (cascade `orphanRemoval` elimina sus sectores),
  y recrea la grilla.
Rangos válidos: `macroZonas` y `sectoresPorMacroZona` enteros positivos dentro de límites
operativos (1..50 macro-zonas, 1..500 sectores); fuera de rango →
`TopologiaInvalidaException` → **400**.

### 5. Conteo de sectores dinámico en `NurseryService`
`getSnapshot()` armaba `Stats` con `600` hardcodeado (total y `% sano`). Pasa a usar el
conteo real de sectores cargados, para que el dashboard sea correcto con cualquier
topología generada.

### 6. DTOs y endpoints
`NuevaTopologia(int macroZonas, int sectoresPorMacroZona, boolean regenerar)` (request) y
`TopologiaVivero(int macroZonas, int sectoresPorMacroZona, int totalSectores, boolean generada)`
(resumen). `TopologiaController` expone `GET /api/topologia` (resumen) y `POST /api/topologia`
(genera/regenera y devuelve el resumen), con `@ExceptionHandler` locales: inválido → 400,
conflicto → 409, body `{ "error": msg }` (mismo patrón que `HardwareController`).

### 7. Capa de datos y vista del frontend
`DataRepository` gana `getTopologia()` y `generarTopologia(input)`. `HttpRepository` hace
`GET`/`POST {baseUrl}/topologia` desempaquetando `{ error }` igual que `mutateDevice`.
`MockRepository` guarda un `topologiaCache` (default 6×100); `generarTopologia` valida (espeja
al backend, lanza), actualiza el cache e **invalida** los caches de nursery y de flota para
que el mapa y la vista de hardware reflejen la nueva grilla. Los generadores mock
(`generators.ts`, `hardware.ts`) leen la topología en vez de constantes fijas. La vista
`features/topologia/` muestra el resumen actual y un formulario (N × M) con **confirmación
explícita** al regenerar.

## Risks / Trade-offs

- **Seed vs. regeneración al reiniciar:** `data.sql` corre en cada arranque
  (`spring.sql.init.mode=always`, `ON CONFLICT DO NOTHING`). Tras regenerar a una grilla
  distinta, un reinicio re-inserta las zonas/sectores demo originales que falten. Aceptable
  para el demo del PFC. **Mitigación** si se quiere persistencia entre reinicios:
  `spring.sql.init.mode=never` (o gatear el seed a "tabla vacía") — decisión operativa.
- **Referencias colgantes:** `dispositivo`/`historial_evento` referencian sector/zona por
  string sin FK; la regeneración los limpia con `deleteAll()` para no dejar punteros a
  sectores inexistentes. Trade-off: regenerar descarta el hardware registrado y el historial.
- **Conteo dinámico:** sustituir el `600` por `allSectors.size()` corrige el dashboard a
  costa de depender de que la grilla esté cargada (vivero vacío → total 0, coherente).

## Migration Plan

1. Backend — DTOs `NuevaTopologia`/`TopologiaVivero` + excepciones
   `TopologiaInvalidaException`/`TopologiaConflictoException`.
2. Backend — `TopologiaService` (getTopologia + generar/regenerar con defaults offline) +
   `TopologiaController` (`GET`/`POST`).
3. Backend — `NurseryService.getSnapshot()` con conteo de sectores dinámico.
4. Frontend — tipos `TopologiaVivero`/`NuevaTopologia`; `getTopologia`/`generarTopologia`
   en repository/http/mock; re-cableado de `generators.ts`/`hardware.ts` para leer la
   topología.
5. Frontend — hook `useTopologia`; feature `topologia/` (page + form con confirmación);
   router + sidebar.
6. Tests — mock determinístico de topología (generación/ids/rango/conflicto/regeneración);
   build del backend.
