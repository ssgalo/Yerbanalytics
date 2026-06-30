# Tasks: add-generacion-topologia

## 1. Backend — DTOs y excepciones
- [x] 1.1 `dto/NuevaTopologia.java` — `int macroZonas`, `int sectoresPorMacroZona`, `boolean regenerar`
- [x] 1.2 `dto/TopologiaVivero.java` — `int macroZonas`, `int sectoresPorMacroZona`, `int totalSectores`, `boolean generada`
- [x] 1.3 `service/TopologiaInvalidaException.java` (400) · `service/TopologiaConflictoException.java` (409)

## 2. Backend — servicio y endpoint
- [x] 2.1 `service/TopologiaService.getTopologia()` — resumen derivado de `zona`/`sector` (`generada = !zonas.isEmpty()`)
- [x] 2.2 `service/TopologiaService.generar(dto)` — valida rangos (400), 409 si existe y `!regenerar`, regenera limpiando dispositivos/historial, crea zonas `MZ-{z}` + sectores `MZ-{z}-{NNN}` con defaults offline
- [x] 2.3 `controller/TopologiaController.java` — `GET`/`POST /api/topologia`, 400/409 en validación
- [x] 2.4 `service/NurseryService.getSnapshot()` — conteo de sectores dinámico (reemplaza el literal `600`)

## 3. Frontend — capa de datos
- [x] 3.1 `types/domain.ts` — `TopologiaVivero`, `NuevaTopologia`
- [x] 3.2 `data/repository.ts` — `getTopologia()`, `generarTopologia(input)`
- [x] 3.3 `data/http/httpRepository.ts` — `GET`/`POST {baseUrl}/topologia` con desempaquetado de error
- [x] 3.4 `data/mock/topologia.ts` — default 6×100, validación (rango/conflicto) que lanza, generadores de id y name/sub
- [x] 3.5 `data/mock/mockRepository.ts` — `topologiaOverride` + `getTopologia`/`generarTopologia` que invalidan los caches de nursery y flota
- [x] 3.6 `data/mock/generators.ts` (grilla offline por topología, conteo dinámico) y `data/mock/hardware.ts` (zonas disponibles vía `setZonasDisponibles`)

## 4. Frontend — vista
- [x] 4.1 `hooks/useTopologia.ts` — fetch + estado de generación (`generating`, `generar`)
- [x] 4.2 `features/topologia/TopologiaPage.tsx` (+ `Topologia.module.css`) — resumen actual + formulario (N×M) con confirmación de regeneración, `usePageTitle`
- [x] 4.3 `router.tsx` — ruta `topologia` bajo `AppLayout`
- [x] 4.4 `components/layout/Sidebar.tsx` — ítem de navegación (icono `cube`)

## 5. Tests y verificación
- [x] 5.1 `data/mock/topologia.test.ts` — generación/conteo de ids, unicidad/formato, rango inválido, conflicto sin `regenerar`, regeneración exitosa
- [x] 5.2 `npm run lint` (0 warnings), `npm run test` (37 OK), `npm run build` en frontend — OK
- [x] 5.3 Compilación del backend (`mvn compile`, OK). Runtime end-to-end (`GET/POST /api/topologia` con Postgres) queda para validar en el entorno del equipo
- [x] 5.4 Revisar que los specs cubren HU-18 CA-01
