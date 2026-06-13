# Design: add-nursery-service

## Context
El frontend ya genera un snapshot determinístico vía `MockRepository`. El backend debe ofrecer el mismo contrato cuando `VITE_DATA_SOURCE=http`, sin romper el flujo de desarrollo UI aislado.

## Decisions

### 1. Generador determinístico (no dump JSON)
Se porta la lógica de `buildNursery()` a Java en lugar de servir un JSON estático. Motivos:
- misma semilla → mismos datos (validación de paridad)
- prueba serialización real de DTOs/records
- reemplazo futuro por fuente real sin cambiar el endpoint

### 2. RNG idéntico (mulberry32)
`Mulberry32Rng` replica `createRng()` de `frontend/src/lib/rng.ts`. El orden de llamadas a `next()` define la salida; no se reordenan.

### 3. Constantes de presentación
`NurseryConstants` centraliza paletas, specs, zonas, plantillas y mapas (`sevMap`, `tints`, etc.) portados de `specs.ts`.

### 4. NurseryService con caché en memoria
Como `MockRepository`, el servicio cachea el snapshot por instancia (singleton Spring). Evita regenerar 600 sectores en cada request durante desarrollo.

### 5. Semilla configurable
`yerbanalytics.mock.seed` en `application.properties` (default `20260613`), alineado con `VITE_MOCK_SEED`.

### 6. Mock dual: front y back coexisten
| Modo | Origen |
|---|---|
| `VITE_DATA_SOURCE=mock` | `MockRepository` (frontend) |
| `VITE_DATA_SOURCE=http` | `NurseryService` (backend) |

## Risks / Trade-offs
- **Duplicación TS/Java:** temporal hasta datos reales. Mitigación: tests de stats determinísticas con semilla fija.
- **Deriva de contrato:** si cambia `domain.ts`, hay que actualizar DTOs y generador. Mitigación: OpenSpec + tests.
