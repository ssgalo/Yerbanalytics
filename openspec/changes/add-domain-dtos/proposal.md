# Change: add-domain-dtos

## Why
El frontend de Yerbanalytics tiene un contrato de datos estricto definido en `frontend/src/types/domain.ts`. Para que el frontend pueda consumir los datos del backend sin romperse ni requerir cambios en sus componentes, el backend debe exponer una estructura JSON exactamente igual.

## What Changes
- Se crea el paquete `dto` dentro de la arquitectura del backend.
- Se implementan todos los tipos del dominio del frontend como **Java Records** (Java 17), garantizando inmutabilidad y reduciendo el código repetitivo.
- Se actualiza el endpoint `GET /api/nursery` en `NurseryController` para que devuelva formalmente la estructura `NurseryData`.

## Impact
- **Affected specs:** `backend-core`.
- **Affected code:** `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/dto/*`, `NurseryController.java`.
- **Frontend:** Ninguno. El objetivo de este cambio es igualar el contrato que el frontend ya espera.

## Non-Goals
- No se implementará todavía la lógica que llena estos datos con información real de BD o IA, solo se define el "molde" (contrato de la API).