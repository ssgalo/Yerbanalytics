# Design: add-domain-dtos

## Context
El backend necesita modelar las 20+ interfaces TypeScript presentes en `domain.ts`.

## Decisions

### 1. Uso de Java Records
Al utilizar Java 17, modelaremos los Objetos de Transferencia de Datos (DTOs) usando `record`. Esto provee inmutabilidad, código limpio y compatibilidad nativa con Jackson (la librería de serialización JSON de Spring).

### 2. Mapeo de Tipos TypeScript a Java
- `string` -> `String`
- `number` -> `Double` o `Integer` (dependiendo del contexto lógico: `conf` es Double, conteos como `total` son Integer).
- `boolean` -> `Boolean`
- Union Types (`Status` / `Severity`) -> `String`. (Se evita usar `enum` estricto en esta etapa para prevenir fallos tempranos de deserialización; la validación se hará a nivel de servicio).
- `Record<string, T>` -> `Map<String, T>`
- `T[]` -> `List<T>`
- Primitivos nullables (`number | null`) -> Clases Wrapper (`Double`, no `double`) para soportar correctamente el valor nulo en JSON.

### 3. Convención de Nombres
Los nombres de los atributos en Java coincidirán **exactamente** con el camelCase de TypeScript, de esta manera el auto-mapeo a JSON es transparente.

### 4. Alcance de SectorDetail
Los records relacionados a `SectorDetail` (`ActuatorRow`, `HistoryEntry`, `Evolution`, `DiagnosisDetail`) preparan el terreno para un futuro endpoint de detalle (`GET /api/sector/{id}`), aunque actualmente no son parte del snapshot devuelto en `NurseryData`.