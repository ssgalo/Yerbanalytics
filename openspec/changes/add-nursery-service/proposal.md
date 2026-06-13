# Change: add-nursery-service

## Why
Los DTOs (`add-domain-dtos`) definen el contrato JSON de `GET /api/nursery`, pero el endpoint aún devuelve `null`. Sin una capa de servicio, el frontend no puede operar en modo `VITE_DATA_SOURCE=http`.

## What Changes
- Se crea `NurseryService` que construye y devuelve un `NurseryData` completo.
- Se porta al backend el generador determinístico del mock del frontend (`generators.ts` + `specs.ts` + `rng.ts`), con la misma semilla por defecto (`20260613`).
- Se conecta `NurseryController` al servicio.
- Se agregan tests de paridad estructural y determinismo en el backend.

## Impact
- **Affected specs:** `nursery-api` (nuevo delta).
- **Affected code:** `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/service/*`, `NurseryController.java`, `application.properties`, tests.
- **Frontend:** Sin cambios. El mock local (`MockRepository`) se mantiene para `VITE_DATA_SOURCE=mock`.

## Non-Goals
- No se conecta BD, sensores ni modelo de IA.
- No se expone `SectorDetail` por API (sigue derivándose en el cliente).
- No se elimina el mock del frontend.
