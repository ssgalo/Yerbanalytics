## Context

La feature de topología (`add-generacion-topologia`, HU-18 CA-01) ya genera la grilla lógica
N×M full-stack. La vista (`features/topologia/TopologiaPage.tsx`) es un formulario con cards de
resumen; no hay preview visual. La disposición en filas del panel general (`ViveroOverview`:
`repeat(3,1fr)` macro-zonas, `repeat(10,1fr)` sectores) y del mapa (`SectorGrid`:
`repeat(10,1fr)`) está hardcodeada en CSS, asumiendo 6×100.

El backend deriva el resumen contando entidades `ZonaEntity`/`SectorEntity`; no persiste nada
de presentación. Ya existe el patrón de configuración de fila única
`ConfiguracionOperativaEntity` (id=1) que sirve de molde para guardar la disposición.

Los paneles consumen el snapshot `NurseryData` vía `useNurseryData()`; la disposición debe
llegar por ese mismo camino para que panel general y mapa la apliquen sin fetch extra.

## Goals / Non-Goals

**Goals:**
- Creación de topología visual: preview que replica el panel general con los valores del form.
- Definir la disposición por fila (`macroZonasPorFila`, `sectoresPorFila`) por arrastre desde
  la esquina (estilo ventana) y por input numérico, sincronizados.
- Persistir la disposición y aplicarla en el panel general y el mapa reales.
- Guardar disposición sin regenerar la grilla (no destructivo).
- No regresión: defaults 3/10 dejan los paneles idénticos a hoy.

**Non-Goals:**
- El resize NO cambia las cantidades (`macroZonas`/`sectoresPorMacroZona`): solo la disposición.
- No se cambia la convención de ids ni la lógica de regeneración destructiva existente.
- No hay disposición por macro-zona individual: `sectoresPorFila` es global.
- No se introducen librerías de drag/resize.

## Decisions

### 1. La disposición es presentación, persistida aparte de la grilla
`macroZonasPorFila`/`sectoresPorFila` no afectan zonas/sectores. Se guardan en una entidad de
fila única `TopologiaLayoutEntity` (id=1), siguiendo `ConfiguracionOperativaEntity`. Sembrada en
`data.sql` con `(1, 3, 10)`. Alternativa descartada: agregar columnas a `ConfiguracionOperativaEntity`
— mezcla dominios (la regeneración de topología no debe tocar la config operativa).

### 2. Guardado de disposición independiente de la generación
Cambiar la disposición es no destructivo, así que NO pasa por el flujo de regeneración. Nuevo
`PUT /api/topologia/disposicion` (body `DisposicionTopologia`) que valida rangos y guarda sin
tocar zonas/sectores/dispositivos. `POST /api/topologia` (generar) sigue existiendo e incluye la
disposición en su payload para guardarla junto con la nueva grilla. Alternativa descartada:
plegar todo en `POST` — obligaría a regenerar (destructivo) solo para reacomodar la vista.

### 3. La disposición viaja en el snapshot `NurseryData`
Se agrega un campo `layout { macroZonasPorFila, sectoresPorFila }` a `NurseryData`
(frontend `domain.ts` + backend record + `NurseryService` lo lee del repo). Así
`ViveroOverview` y `SectorGrid` lo aplican vía el `useNurseryData()` que ya usan, sin fetch
extra. Las columnas se setean con inline `style={{ gridTemplateColumns: \`repeat(${n},1fr)\` }}`,
reemplazando los `repeat(...)` fijos de los CSS Modules.

### 4. Validación: disposición acotada por las cantidades (espejo mock↔backend)
`macroZonasPorFila` ∈ [1, `macroZonas`]; `sectoresPorFila` ∈ [1, `sectoresPorMacroZona`]. Se
valida en cliente (`disposicionError` en `data/mock/topologia.ts`) y en backend
(`TopologiaService`, lanza `TopologiaInvalidaException` → 400), igual que la validación de
cantidades existente. Al guardar al generar, la disposición se clampa a las nuevas cantidades.

### 5. Resize con Pointer Events nativos
El preview tiene dos grips (esquina inferior derecha): uno en una macro-zona de muestra cambia
`sectoresPorFila`; otro en el contenedor general cambia `macroZonasPorFila`. Con
`onPointerDown` + `setPointerCapture` + `onPointerMove`, el delta horizontal en px se divide por
el ancho de celda medido (`ref`/`getBoundingClientRect`), se snapea a entero y se clampa al
rango. El alto reflowa solo (`filas = ceil(cantidad / porFila)`), dando la sensación de
redimensionar una ventana. Los inputs numéricos editan el mismo estado (two-way). Alternativa
descartada: librería de resize — innecesaria para un grip de un solo eje.

## Risks / Trade-offs

- **Preview con muchas celdas (p. ej. 50×500)** puede ser pesado de renderizar →
  el preview dibuja celdas placeholder ligeras (`<span>` sin datos) y, si hace falta, se
  limita el tamaño visual de la celda; el caso normal (≤6×100) es trivial.
- **Drift de validación mock vs backend** → se mantienen como espejo explícito, como ya se hace
  con las cantidades; cubierto por los tests de `topologia.test.ts`.
- **`sectoresPorFila` global, no por macro-zona** → simplifica el modelo y alcanza el objetivo;
  si se quisiera por zona, sería un cambio futuro aditivo.
- **Campo nuevo en `NurseryData`** → aditivo; clientes viejos lo ignoran. Se siembra default
  para que no haya estados nulos.
