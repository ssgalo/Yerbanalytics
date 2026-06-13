# Spec: nursery-api

## ADDED Requirements

### Requirement: Snapshot completo del vivero
El endpoint `GET /api/nursery` SHALL devolver un JSON que cumple la forma `NurseryData`
definida en `frontend/src/types/domain.ts`.

#### Scenario: Respuesta no vacía
- **WHEN** se invoca `GET /api/nursery`
- **THEN** el cuerpo es un objeto `NurseryData` con `zonas`, `sectors`, `byId`, `stats` y el resto de campos requeridos
- **AND** no es `null`

### Requirement: Generación determinística en el backend
El backend SHALL generar el snapshot con un RNG mulberry32 y semilla configurable,
replicando la lógica del mock del frontend.

#### Scenario: Misma semilla, mismas stats
- **WHEN** se genera el snapshot dos veces con semilla `20260613`
- **THEN** las stats agregadas (`sano`, `warning`, `critical`, `offline`, `diagCount`) son idénticas

#### Scenario: Estructura del vivero
- **WHEN** se genera con la semilla por defecto
- **THEN** hay 6 macro-zonas con 100 sectores cada una (600 total)
- **AND** `stats.total` es 600
- **AND** `stats.sano + stats.warning + stats.critical + stats.offline` es 600

### Requirement: Mock del frontend intacto
El cambio SHALL NOT modificar `MockRepository` ni `VITE_DATA_SOURCE=mock`.

#### Scenario: Desarrollo UI sin backend
- **WHEN** `VITE_DATA_SOURCE` es `mock` o no está definida
- **THEN** el frontend sigue usando `MockRepository` localmente
